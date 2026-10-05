package nl.banksim.bff.health;

import java.util.concurrent.atomic.AtomicBoolean;

import nl.banksim.bff.BffProperties;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Startup-poort: UP zodra Keycloak één keer bereikbaar was. Een latere storing maakt de pod niet unready
 * (TO §12.1); nieuwe logins falen dan met een nette melding.
 */
@Component("keycloak")
class KeycloakHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final String jwksUri;
    private final AtomicBoolean reachedOnce = new AtomicBoolean();

    KeycloakHealthIndicator(BffProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.keycloak().timeout());
        factory.setReadTimeout(properties.keycloak().timeout());
        this.restTemplate = new RestTemplate(factory);
        this.jwksUri = properties.keycloak().internalEndpoint("certs");
    }

    @Override
    public Health health() {
        if (reachedOnce.get()) {
            return Health.up().build();
        }
        try {
            restTemplate.getForObject(jwksUri, String.class);
            reachedOnce.set(true);
            return Health.up().build();
        } catch (RestClientException e) {
            return Health.down().withDetail("reason", "Keycloak nog niet bereikbaar").build();
        }
    }
}
