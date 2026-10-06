package nl.banksim.datagen.keycloak;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import nl.banksim.datagen.DatagenProperties;
import nl.banksim.datagen.model.Rekeninghouder;

/**
 * Beheert gebruikers via de Admin REST API van Keycloak met de service-account-client {@code bank-datagen}
 * (alleen gebruikers beheren, TO §9). Idempotent: bestaande gebruikers worden bijgewerkt, niet opnieuw gemaakt,
 * zodat hun Keycloak-id ({@code sub}) gelijk blijft.
 */
@Component
class KeycloakGebruikersBeheer implements GebruikersBeheer {

    private static final Logger log = LoggerFactory.getLogger(KeycloakGebruikersBeheer.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIJST = new ParameterizedTypeReference<>() {
    };

    private final DatagenProperties properties;
    private final RestClient client;

    @Autowired
    KeycloakGebruikersBeheer(DatagenProperties properties, RestClient.Builder builder) {
        this(properties, builder.requestFactory(requestFactory(properties))
                .baseUrl(properties.keycloak().internalUrl().toString()).build());
    }

    KeycloakGebruikersBeheer(DatagenProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** JDK-client met timeouts; de JVM-brede SSLContext levert het clientcertificaat voor Keycloak. */
    private static JdkClientHttpRequestFactory requestFactory(DatagenProperties properties) {
        var http = HttpClient.newBuilder().connectTimeout(properties.keycloak().timeout()).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.keycloak().timeout());
        return factory;
    }

    @Override
    public Map<String, String> zorgVoorKlanten(List<Rekeninghouder> klanten) {
        String token = token();
        Map<String, Object> klantRol = rol(token, "klant");
        Map<String, String> ids = new LinkedHashMap<>();
        for (Rekeninghouder klant : klanten) {
            String id = zorgVoorGebruiker(token, klant.gebruikersnaam(), klant.voornaam(), klant.achternaam(),
                    properties.klantWachtwoord(), klantRol);
            ids.put(klant.gebruikersnaam(), id);
        }
        log.info("{} klanten aanwezig in Keycloak", ids.size());
        return ids;
    }

    @Override
    public void zorgVoorBeheerder() {
        String token = token();
        zorgVoorGebruiker(token, properties.beheerder(), "BankSim", "Beheerder", properties.beheerderWachtwoord(),
                rol(token, "admin"));
        log.info("Beheerder '{}' aanwezig in Keycloak", properties.beheerder());
    }

    private String zorgVoorGebruiker(String token, String gebruikersnaam, String voornaam, String achternaam,
                                     String wachtwoord, Map<String, Object> rol) {
        Map<String, Object> gebruiker = Map.of(
                "username", gebruikersnaam,
                "enabled", true,
                "firstName", voornaam,
                "lastName", achternaam,
                "email", gebruikersnaam + "@banksim.local",
                "emailVerified", true);

        List<Map<String, Object>> gevonden = client.get()
                .uri(admin("/users?exact=true&username={u}"), gebruikersnaam)
                .headers(h -> h.setBearerAuth(token))
                .retrieve().body(LIJST);
        String id;
        if (gevonden == null || gevonden.isEmpty()) {
            URI locatie = client.post().uri(admin("/users"))
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).body(gebruiker)
                    .retrieve().toBodilessEntity().getHeaders().getLocation();
            if (locatie == null) {
                throw new IllegalStateException("Keycloak gaf geen locatie voor nieuwe gebruiker " + gebruikersnaam);
            }
            String pad = locatie.getPath();
            id = pad.substring(pad.lastIndexOf('/') + 1);
        } else {
            id = String.valueOf(gevonden.getFirst().get("id"));
            client.put().uri(admin("/users/{id}"), id)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).body(gebruiker)
                    .retrieve().toBodilessEntity();
        }
        client.put().uri(admin("/users/{id}/reset-password"), id)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", wachtwoord, "temporary", false))
                .retrieve().toBodilessEntity();
        client.post().uri(admin("/users/{id}/role-mappings/realm"), id)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(List.of(rol))
                .retrieve().toBodilessEntity();
        return id;
    }

    private Map<String, Object> rol(String token, String naam) {
        return client.get().uri(admin("/roles/{rol}"), naam)
                .headers(h -> h.setBearerAuth(token))
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    private String token() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.keycloak().clientId());
        form.add("client_secret", properties.keycloak().clientSecret());
        Map<String, Object> antwoord = client.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.keycloak().realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        if (antwoord == null || !(antwoord.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Geen access token van Keycloak");
        }
        return token;
    }

    private String admin(String pad) {
        return "/admin/realms/" + properties.keycloak().realm() + pad;
    }
}
