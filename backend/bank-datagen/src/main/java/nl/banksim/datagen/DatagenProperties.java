package nl.banksim.datagen;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param uitvoeren           false in tests: dan start de generatie niet automatisch
 * @param modus               ALS_LEEG bij installatie/upgrade, ALTIJD voor reset-data.sh
 * @param seed                vaste seed: zelfde seed geeft dezelfde data
 * @param klantWachtwoord     wachtwoord van alle klant-gebruikers (uit een Secret)
 * @param beheerderWachtwoord wachtwoord van de admin-gebruiker (uit een Secret)
 */
@ConfigurationProperties("banksim.datagen")
public record DatagenProperties(boolean uitvoeren, Modus modus, long seed, LocalDate vanaf, LocalDate tot,
                                String klantWachtwoord, String beheerder, String beheerderWachtwoord,
                                Keycloak keycloak) {

    public enum Modus { ALS_LEEG, ALTIJD }

    public record Keycloak(URI internalUrl, String realm, String clientId, String clientSecret, Duration timeout) {

        public Keycloak {
            if (timeout == null) {
                timeout = Duration.ofSeconds(5);
            }
        }
    }
}
