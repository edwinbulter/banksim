package nl.banksim.bff.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/** Antwoord als bank-api niet bereikbaar is of de circuit breaker open staat (TO §12.2). */
@Configuration
class ApiFallback {

    @Bean
    RouterFunction<ServerResponse> apiFallbackRoute() {
        return RouterFunctions.route()
                .route(request -> request.path().equals(ApiRoute.FALLBACK),
                        request -> ApiRoute.probleem(HttpStatus.SERVICE_UNAVAILABLE, "tijdelijk-niet-beschikbaar",
                                "Tijdelijk niet beschikbaar",
                                "De bank is even niet bereikbaar. Probeer het over een paar seconden opnieuw."))
                .build();
    }
}
