package nl.banksim.api.security;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Startup-poort: UP zodra de signeersleutels van Keycloak één keer zijn opgehaald. Een latere storing van
 * Keycloak maakt de pod bewust niet unready (TO §12.1).
 */
@Component("jwks")
class JwksHealthIndicator implements HealthIndicator {

    private final RestTemplate jwksRestTemplate;
    private final BanksimSecurityProperties properties;
    private final AtomicBoolean loadedOnce = new AtomicBoolean();

    JwksHealthIndicator(RestTemplate jwksRestTemplate, BanksimSecurityProperties properties) {
        this.jwksRestTemplate = jwksRestTemplate;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (loadedOnce.get()) {
            return Health.up().build();
        }
        try {
            jwksRestTemplate.getForObject(properties.jwkSetUri(), String.class);
            loadedOnce.set(true);
            return Health.up().build();
        } catch (RestClientException e) {
            return Health.down().withDetail("reason", "JWKS nog niet bereikbaar").build();
        }
    }
}
