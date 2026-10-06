package nl.banksim.datagen.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import nl.banksim.datagen.DatagenProperties;
import nl.banksim.datagen.model.Rekeninghouder;

class KeycloakGebruikersBeheerTests {

    static final String BASIS = "https://keycloak.test:8443";
    static final String ADMIN = BASIS + "/admin/realms/banksim";

    final DatagenProperties properties = new DatagenProperties(false, DatagenProperties.Modus.ALTIJD, 42,
            LocalDate.of(2021, 10, 1), LocalDate.of(2026, 12, 31), "klant-geheim-123", "beheerder", "beheer-geheim-123",
            new DatagenProperties.Keycloak(URI.create(BASIS), "banksim", "bank-datagen", "client-geheim", null));

    MockRestServiceServer server;
    KeycloakGebruikersBeheer beheer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASIS);
        server = MockRestServiceServer.bindTo(builder).build();
        beheer = new KeycloakGebruikersBeheer(properties, builder.build());
    }

    @Test
    void maaktNieuweKlantAanMetWachtwoordEnRol() {
        verwachtToken();
        verwachtRol("klant");
        server.expect(requestTo(ADMIN + "/users?exact=true&username=jdevries")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"username\":\"jdevries\",\"firstName\":\"Jan\",\"lastName\":\"de Vries\",\"enabled\":true}"))
                .andRespond(withCreatedEntity(URI.create(ADMIN + "/users/kc-123")));
        server.expect(requestTo(ADMIN + "/users/kc-123/reset-password")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"type\":\"password\",\"value\":\"klant-geheim-123\",\"temporary\":false}"))
                .andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-123/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"name\":\"klant\"}]"))
                .andRespond(withNoContent());

        Map<String, String> ids = beheer.zorgVoorKlanten(List.of(jan()));

        assertThat(ids).containsEntry("jdevries", "kc-123");
        server.verify();
    }

    @Test
    void bestaandeKlantBehoudtZijnId() {
        verwachtToken();
        verwachtRol("klant");
        server.expect(requestTo(ADMIN + "/users?exact=true&username=jdevries"))
                .andRespond(withSuccess("[{\"id\":\"kc-bestaand\",\"username\":\"jdevries\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/kc-bestaand")).andExpect(method(HttpMethod.PUT)).andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-bestaand/reset-password")).andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-bestaand/role-mappings/realm")).andRespond(withNoContent());

        assertThat(beheer.zorgVoorKlanten(List.of(jan()))).containsEntry("jdevries", "kc-bestaand");
        server.verify();
    }

    @Test
    void beheerderKrijgtDeAdminRol() {
        verwachtToken();
        verwachtRol("admin");
        server.expect(requestTo(ADMIN + "/users?exact=true&username=beheerder"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users")).andRespond(withCreatedEntity(URI.create(ADMIN + "/users/kc-admin")));
        server.expect(requestTo(ADMIN + "/users/kc-admin/reset-password"))
                .andExpect(content().json("{\"value\":\"beheer-geheim-123\"}")).andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-admin/role-mappings/realm"))
                .andExpect(content().json("[{\"name\":\"admin\"}]")).andRespond(withNoContent());

        beheer.zorgVoorBeheerder();
        server.verify();
    }

    private void verwachtToken() {
        server.expect(requestTo(BASIS + "/realms/banksim/protocol/openid-connect/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("grant_type", "client_credentials", "client_id", "bank-datagen")))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
    }

    private void verwachtRol(String rol) {
        server.expect(requestTo(ADMIN + "/roles/" + rol))
                .andRespond(withSuccess("{\"id\":\"r-1\",\"name\":\"" + rol + "\"}", MediaType.APPLICATION_JSON));
    }

    private static Rekeninghouder jan() {
        return new Rekeninghouder(UUID.randomUUID(), "Jan de Vries", Rekeninghouder.Soort.HUISHOUDEN, "jdevries", "Jan", "de Vries");
    }
}
