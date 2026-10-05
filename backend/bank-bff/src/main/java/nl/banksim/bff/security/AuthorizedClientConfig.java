package nl.banksim.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Access- en refresh-tokens horen in de sessie, die via Spring Session JDBC door alle BFF-replica's wordt
 * gedeeld. De standaard van Spring Boot bewaart ze in het geheugen van één pod, waardoor een request op een
 * andere replica opnieuw moet inloggen.
 */
@Configuration
class AuthorizedClientConfig {

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }
}
