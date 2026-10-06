package nl.banksim.bff.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.Cookie;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import nl.banksim.bff.BffTest;
import nl.banksim.bff.security.TestRegistrations;

/** TO §12.2: doorsturen naar bank-api met token, retry alleen voor GET, circuit breaker en rate limiting. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRouteTests {

    static final WireMockServer API = new WireMockServer(options().dynamicPort());

    static {
        API.start();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    final JdbcTemplate beheer = new JdbcTemplate(new DriverManagerDataSource(BffTest.POSTGRES.getJdbcUrl(),
            BffTest.POSTGRES.getUsername(), BffTest.POSTGRES.getPassword()));

    @DynamicPropertySource
    static void eigenschappen(DynamicPropertyRegistry registry) {
        BffTest.registreer(registry);
        registry.add("banksim.bff.api-url", () -> "http://localhost:" + API.port());
        registry.add("banksim.bff.limiet.boekingen-per-minuut", () -> "3");
        registry.add("banksim.bff.limiet.login-per-minuut", () -> "5");
    }

    @BeforeEach
    void schoon() {
        API.resetAll();
        circuitBreakers.circuitBreaker("bank-api").reset();
        beheer.update("DELETE FROM bff.bucket");
    }

    @Test
    void stuurtAccessTokenDoorZonderBrowsercookies() throws Exception {
        API.stubFor(get("/api/me").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"naam\":\"Jan de Vries\"}")));

        uitvoeren(request(HttpMethod.GET, "/api/me").with(ingelogd()).cookie(new Cookie("__Host-SESSION", "abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naam").value("Jan de Vries"));

        API.verify(getRequestedFor(urlEqualTo("/api/me"))
                .withHeader("Authorization", equalTo("Bearer access-token"))
                .withHeader("Cookie", absent())
                .withHeader("X-XSRF-TOKEN", absent()));
    }

    @Test
    void getWordtBijEenServerfoutHerhaald() throws Exception {
        API.stubFor(get("/api/me/accounts").inScenario("herstel").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("tweede"));
        API.stubFor(get("/api/me/accounts").inScenario("herstel").whenScenarioStateIs("tweede")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("derde"));
        API.stubFor(get("/api/me/accounts").inScenario("herstel").whenScenarioStateIs("derde")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"rekeningen\":[]}")));

        uitvoeren(request(HttpMethod.GET, "/api/me/accounts").with(ingelogd())).andExpect(status().isOk());
        API.verify(3, getRequestedFor(urlEqualTo("/api/me/accounts")));
    }

    @Test
    void postWordtNooitAutomatischHerhaald() throws Exception {
        API.stubFor(post("/api/payments").willReturn(aResponse().withStatus(503)
                .withHeader("Content-Type", "application/problem+json").withBody("{\"status\":503}")));

        uitvoeren(request(HttpMethod.POST, "/api/payments").with(ingelogd()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());
        API.verify(1, postRequestedFor(urlEqualTo("/api/payments")));
    }

    @Test
    void onbereikbareApiGeeftNette503EnDeCircuitBreakerGaatOpen() throws Exception {
        API.stubFor(get("/api/me").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        for (int i = 0; i < 15; i++) {
            // De circuit breaker stuurt door naar de fallback (servlet-forward; die voert MockMvc niet zelf uit).
            uitvoeren(request(HttpMethod.GET, "/api/me").with(ingelogd())).andExpect(forwardedUrl(ApiRoute.FALLBACK));
        }
        assertThat(circuitBreakers.circuitBreaker("bank-api").getState().name()).isEqualTo("OPEN");
        int aanroepen = API.findAll(getRequestedFor(urlEqualTo("/api/me"))).size();
        assertThat(aanroepen).as("bij een open circuit wordt de API niet meer aangeroepen").isLessThan(15 * 3);
    }

    @Test
    void fallbackGeeftNette503MetRetryAfter() throws Exception {
        mvc.perform(request(HttpMethod.GET, ApiRoute.FALLBACK))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/tijdelijk-niet-beschikbaar"));
    }

    @Test
    void teVeelBoekingenPerMinuutGeeft429() throws Exception {
        API.stubFor(post("/api/payments").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{}")));
        for (int i = 0; i < 3; i++) {
            uitvoeren(request(HttpMethod.POST, "/api/payments").with(ingelogd()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated());
        }
        uitvoeren(request(HttpMethod.POST, "/api/payments").with(ingelogd()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/te-veel-verzoeken"));
        API.verify(3, postRequestedFor(urlEqualTo("/api/payments")));
    }

    @Test
    void teVeelInlogpogingenPerIpGeeft429() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(request(HttpMethod.GET, "/oauth2/authorization/keycloak").header("X-Forwarded-For", "203.0.113.7"))
                    .andExpect(status().is3xxRedirection());
        }
        mvc.perform(request(HttpMethod.GET, "/oauth2/authorization/keycloak").header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(request(HttpMethod.GET, "/oauth2/authorization/keycloak").header("X-Forwarded-For", "203.0.113.8"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void sessieStaatVersleuteldInDeDatabase() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/oauth2/authorization/keycloak")).andExpect(status().is3xxRedirection());

        List<byte[]> attributen = beheer.queryForList("SELECT attribute_bytes FROM bff.spring_session_attributes", byte[].class);
        assertThat(attributen).isNotEmpty().allSatisfy(bytes -> {
            assertThat(bytes[0]).isEqualTo((byte) 1);
            assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
                    .doesNotContain("OAuth2AuthorizationRequest").doesNotContain("org.springframework");
        });
    }

    /** De gateway verwerkt via de circuit breaker asynchroon; MockMvc moet dan expliciet doorgaan. */
    private ResultActions uitvoeren(MockHttpServletRequestBuilder verzoek) throws Exception {
        ResultActions acties = mvc.perform(verzoek);
        MvcResult resultaat = acties.andReturn();
        return resultaat.getRequest().isAsyncStarted() ? mvc.perform(asyncDispatch(resultaat)) : acties;
    }

    private static RequestPostProcessor ingelogd() {
        return oidcLogin().clientRegistration(TestRegistrations.keycloak());
    }
}
