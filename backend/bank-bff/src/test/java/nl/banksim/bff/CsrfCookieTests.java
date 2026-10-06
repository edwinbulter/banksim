package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Het CSRF-token zoals een browser het gebruikt: uit het XSRF-TOKEN-cookie, ruw meegestuurd als header (Angular)
 * of als formulierparameter (uitloggen). Eigen context, want de {@code csrf()}-hulp van Spring Security Test
 * vervangt blijvend de token-opslag van het CsrfFilter in een gedeelde context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BffTest.registreer(registry);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void elkAntwoordZetHetXsrfCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/me")).andReturn();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").isHttpOnly()).isFalse();
    }

    /** Zo doet de browser het: eerst het cookie krijgen, dan de waarde ervan meesturen (header of formulier). */
    @Test
    void ruwTokenUitHetCookieWerktAlsHeaderEnAlsFormulierparameter() throws Exception {
        String token = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        var cookie = new jakarta.servlet.http.Cookie("XSRF-TOKEN", token);

        mvc.perform(post("/api/payments").with(oidcLogin()).cookie(cookie).header("X-XSRF-TOKEN", token))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));

        MvcResult result = mvc.perform(post("/logout")
                        .with(oidcLogin().clientRegistration(nl.banksim.bff.security.TestRegistrations.keycloak()))
                        .cookie(cookie)
                        .param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://auth.localtest.me/realms/banksim/protocol/openid-connect/logout");
    }

}
