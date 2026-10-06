package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class BffSecurityTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BffTest.registreer(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    OAuth2AuthorizedClientRepository authorizedClients;

    @Test
    void tokensStaanInDeGedeeldeSessie() {
        assertThat(authorizedClients).isInstanceOf(HttpSessionOAuth2AuthorizedClientRepository.class);
    }

    @Test
    void apiZonderSessieGeeft401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginGaatNaarPubliekeKeycloakMetPkce() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/keycloak"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://auth.localtest.me/realms/banksim/protocol/openid-connect/auth?")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=https://bank.localtest.me/login/oauth2/code/keycloak");
    }

    @Test
    void uitloggenZonderCsrfTokenWordtGeweigerd() throws Exception {
        mvc.perform(post("/logout").with(oauth2Login())).andExpect(status().isForbidden());
    }

    @Test
    void uitloggenStuurtNaarKeycloakLogout() throws Exception {
        MvcResult result = mvc.perform(post("/logout").with(oidcLogin().clientRegistration(
                        nl.banksim.bff.security.TestRegistrations.keycloak())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://auth.localtest.me/realms/banksim/protocol/openid-connect/logout");
    }

    @Test
    void verkeerdCsrfTokenWordtGeweigerd() throws Exception {
        mvc.perform(post("/logout").with(oidcLogin())
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "echt-token"))
                        .param("_csrf", "ander-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersOpAntwoorden() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void sessiecookieIsHostPrefixedEnStrict() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/keycloak")).andReturn();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("__Host-SESSION=")
                        .contains("Secure").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/"));
    }
}
