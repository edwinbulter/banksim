package nl.banksim.api.me;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "banksim.mtls.enabled=false",
        "server.ssl.enabled=false",
        "banksim.cursor-sleutel=test-sleutel-van-minstens-32-tekens!!",
        "spring.ssl.bundle.pem.server.keystore.certificate=",
        "spring.ssl.bundle.pem.server.keystore.private-key=",
        "spring.ssl.bundle.pem.server.truststore.certificate="
})
@AutoConfigureMockMvc
class MeControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void zonderTokenGeeft401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void zonderBankRolGeeft403() throws Exception {
        mvc.perform(get("/api/me").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void klantKrijgtNaamEnRollen() throws Exception {
        mvc.perform(get("/api/me").with(jwt()
                        .jwt(token -> token.claim("name", "Jan de Vries").claim("preferred_username", "jdevries"))
                        .authorities(new SimpleGrantedAuthority("ROLE_klant"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naam").value("Jan de Vries"))
                .andExpect(jsonPath("$.gebruikersnaam").value("jdevries"))
                .andExpect(jsonPath("$.rollen[0]").value("klant"));
    }

    @Test
    void anderePadenWordenGeweigerd() throws Exception {
        mvc.perform(get("/iets-anders").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_klant"))))
                .andExpect(status().isForbidden());
    }
}
