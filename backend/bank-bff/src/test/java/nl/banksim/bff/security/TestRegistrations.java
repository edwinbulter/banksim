package nl.banksim.bff.security;

import java.net.URI;

import nl.banksim.bff.BffProperties;

import org.springframework.security.oauth2.client.registration.ClientRegistration;

/** Toegang tot de echte Keycloak-registratie voor tests. */
public final class TestRegistrations {

    private TestRegistrations() {
    }

    public static ClientRegistration keycloak() {
        return KeycloakClientRegistration.keycloak(new BffProperties(
                URI.create("https://bank.localtest.me"),
                URI.create("https://bank-api.banksim.svc:8443"),
                new BffProperties.Keycloak(URI.create("https://auth.localtest.me"),
                        URI.create("https://keycloak.banksim.svc:8443"), "banksim", "bank-bff", "test-secret", null), null, null));
    }
}
