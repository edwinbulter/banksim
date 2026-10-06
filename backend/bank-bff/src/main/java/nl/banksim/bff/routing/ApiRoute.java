package nl.banksim.bff.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestParameter;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import nl.banksim.bff.BffProperties;

/**
 * {@code /api/**} naar bank-api: met het access token van de gebruiker, zonder browsercookies en CSRF-token, met een circuit
 * breaker en alleen voor GET een retry met backoff (TO §12.2). POST wordt nooit automatisch herhaald; de
 * frontend mag dat zelf met dezelfde Idempotency-Key.
 */
@Configuration
class ApiRoute {

    static final String FALLBACK = "/bff/fallback/api";
    private static final Logger log = LoggerFactory.getLogger(ApiRoute.class);

    @Bean
    RouterFunction<ServerResponse> bankApiRoute(BffProperties properties) {
        return route("bank-api")
                .route(path("/api/**"), http())
                .before(uri(properties.apiUrl()))
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .before(removeRequestHeader("X-XSRF-TOKEN"))
                .before(removeRequestParameter("_csrf"))
                .filter(tokenRelay())
                .filter(circuitBreaker(config -> config.setId("bank-api").setFallbackPath(FALLBACK)))
                .filter(retry(config -> config.setRetries(2)
                        .setMethods(Set.of(HttpMethod.GET))
                        .setSeries(Set.of(HttpStatus.Series.SERVER_ERROR))
                        .setExceptions(Set.of(IOException.class))
                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2)))
                .onError(OAuth2AuthorizationException.class, ApiRoute::tokenProbleem)
                .build();
    }

    /**
     * Geen of verlopen token (bijvoorbeeld refresh mislukt): 401, zodat de frontend opnieuw laat inloggen. Is
     * Keycloak onbereikbaar, dan 503.
     */
    static ServerResponse tokenProbleem(Throwable fout, ServerRequest request) {
        boolean keycloakWeg = fout instanceof ClientAuthorizationException e && e.getCause() != null
                && !(e.getError().getErrorCode().equals("invalid_grant"));
        if (keycloakWeg) {
            log.warn("Token vernieuwen mislukt: Keycloak niet bereikbaar");
            return probleem(HttpStatus.SERVICE_UNAVAILABLE, "inloggen-niet-mogelijk", "Inloggen tijdelijk niet mogelijk",
                    "Je sessie kan nu niet worden verlengd. Probeer het over een paar seconden opnieuw.");
        }
        return probleem(HttpStatus.UNAUTHORIZED, "opnieuw-inloggen", "Opnieuw inloggen",
                "Je sessie is verlopen. Log opnieuw in.");
    }

    static ServerResponse probleem(HttpStatus status, String code, String titel, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(java.net.URI.create("https://banksim.local/problems/" + code));
        problem.setTitle(titel);
        var antwoord = ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            antwoord.header(HttpHeaders.RETRY_AFTER, "5");
        }
        return antwoord.body(problem);
    }
}
