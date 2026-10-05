package nl.banksim.api.security;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Beveiligingsinstellingen van bank-api.
 *
 * @param issuer      verwachte {@code iss} in het JWT (publieke Keycloak-URL)
 * @param jwkSetUri   interne URL waar de signeersleutels van Keycloak staan
 * @param audience    verwachte waarde in {@code aud}
 * @param jwksTimeout timeout voor het ophalen van de sleutels
 */
@ConfigurationProperties("banksim.security")
public record BanksimSecurityProperties(String issuer, URI jwkSetUri, String audience, Duration jwksTimeout) {

    public BanksimSecurityProperties {
        if (jwksTimeout == null) {
            jwksTimeout = Duration.ofSeconds(2);
        }
    }
}
