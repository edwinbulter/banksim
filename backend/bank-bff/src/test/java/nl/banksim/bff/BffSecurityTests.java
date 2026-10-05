package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "banksim.mtls.enabled=false",
        "server.ssl.enabled=false",
        "spring.ssl.bundle.pem.server.keystore.certificate=",
        "spring.ssl.bundle.pem.server.keystore.private-key=",
        "spring.ssl.bundle.pem.server.truststore.certificate=",
        "spring.session.jdbc.table-name=SPRING_SESSION",
        "spring.session.jdbc.initialize-schema=always",
        "banksim.bff.keycloak.client-secret=test-secret"
})
@AutoConfigureMockMvc
@Testcontainers
class BffSecurityTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

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
    void sessiecookieIsHostPrefixedEnStrict() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/keycloak")).andReturn();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("__Host-SESSION=")
                        .contains("Secure").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/"));
    }
}
