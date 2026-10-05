package nl.banksim.bff;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl publieke URL van de bank-app, bijvoorbeeld {@code https://bank.localtest.me}
 * @param apiUrl    interne URL van bank-api
 * @param keycloak  Keycloak-instellingen; de browser gebruikt de publieke URL, de BFF de interne
 */
@ConfigurationProperties("banksim.bff")
public record BffProperties(URI publicUrl, URI apiUrl, Keycloak keycloak) {

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
}
