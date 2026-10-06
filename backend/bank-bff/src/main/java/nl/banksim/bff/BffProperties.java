package nl.banksim.bff;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl publieke URL van de bank-app, bijvoorbeeld {@code https://bank.localtest.me}
 * @param apiUrl    interne URL van bank-api
 * @param keycloak  Keycloak-instellingen; de browser gebruikt de publieke URL, de BFF de interne
 * @param sessie    sessiecookie en versleuteling van de sessie in de database
 * @param limiet    rate limiting (TO §10.1)
 */
@ConfigurationProperties("banksim.bff")
public record BffProperties(URI publicUrl, URI apiUrl, Keycloak keycloak, Sessie sessie, Limiet limiet) {

    public BffProperties {
        if (sessie == null) {
            sessie = new Sessie(null, true);
        }
        if (limiet == null) {
            limiet = new Limiet(10, 20);
        }
    }

    /**
     * @param publicUrl    publieke basis-URL van Keycloak (ook de issuer-basis)
     * @param internalUrl  interne basis-URL van Keycloak via cluster-DNS
     * @param realm        realm-naam
     * @param clientId     client-id van de BFF
     * @param clientSecret client secret van de BFF
     * @param timeout      timeout voor calls naar Keycloak
     */
    public record Keycloak(URI publicUrl, URI internalUrl, String realm, String clientId, String clientSecret,
                           Duration timeout) {

        public Keycloak {
            if (timeout == null) {
                timeout = Duration.ofSeconds(3);
            }
        }

        public String issuer() {
            return publicUrl + "/realms/" + realm;
        }

        public String publicEndpoint(String path) {
            return issuer() + "/protocol/openid-connect/" + path;
        }

        public String internalEndpoint(String path) {
            return internalUrl + "/realms/" + realm + "/protocol/openid-connect/" + path;
        }
    }

    /**
     * @param sleutel      geheim waaruit de AES-sleutel voor de sessie-attributen wordt afgeleid (Secret)
     * @param secureCookie alleen in tests zonder TLS uit
     */
    public record Sessie(String sleutel, boolean secureCookie) {
    }

    /**
     * @param loginPerMinuut      inlogpogingen per minuut per IP-adres
     * @param boekingenPerMinuut  betalingen en overschrijvingen per minuut per gebruiker
     */
    public record Limiet(int loginPerMinuut, int boekingenPerMinuut) {
    }
}
