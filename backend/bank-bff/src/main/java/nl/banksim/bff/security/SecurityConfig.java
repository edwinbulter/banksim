package nl.banksim.bff.security;

import nl.banksim.bff.BffProperties;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
class SecurityConfig {

    static final String LOGIN_PATH = "/oauth2/authorization/" + KeycloakClientRegistration.REGISTRATION_ID;

    /** Alleen het health-endpoint is blootgesteld, op de aparte management-poort voor de kubelet-probes. */
    @Bean
    @Order(1)
    SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    SecurityFilterChain bffSecurity(HttpSecurity http, ClientRegistrationRepository registrations,
                                    BffProperties properties) throws Exception {
        var authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, "/oauth2/authorization");
        authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutSuccessHandler.setPostLogoutRedirectUri(properties.publicUrl() + "/");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/**", "/logout").authenticated()
                        .anyRequest().denyAll())
                .oauth2Login(login -> login
                        .loginPage(LOGIN_PATH)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .defaultSuccessUrl(properties.publicUrl() + "/", true))
                // De Angular-app krijgt 401 en stuurt de browser zelf naar de login.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")))
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
                // XSRF-TOKEN-cookie + X-XSRF-TOKEN-header, zoals Angular HttpClient standaard doet.
                .csrf(csrf -> csrf.spa());
        return http.build();
    }
}
