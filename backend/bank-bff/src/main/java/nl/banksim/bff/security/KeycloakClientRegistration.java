package nl.banksim.bff.security;

import java.util.Map;

import nl.banksim.bff.BffProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * De Keycloak-client wordt expliciet geconfigureerd in plaats van met {@code issuer-uri}: er is dan geen
 * discovery bij startup (de BFF start ook als Keycloak nog niet bereikbaar is) en de browser gebruikt de
 * publieke host terwijl de BFF zelf via cluster-DNS praat (TO §10.2).
 */
@Configuration
class KeycloakClientRegistration {

    static final String REGISTRATION_ID = "keycloak";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(BffProperties properties) {
        return new InMemoryClientRegistrationRepository(keycloak(properties));
    }

    static ClientRegistration keycloak(BffProperties properties) {
        BffProperties.Keycloak keycloak = properties.keycloak();
        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(keycloak.clientId())
                .clientSecret(keycloak.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.publicUrl() + "/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .authorizationUri(keycloak.publicEndpoint("auth"))
                .tokenUri(keycloak.internalEndpoint("token"))
                .jwkSetUri(keycloak.internalEndpoint("certs"))
                .issuerUri(keycloak.issuer())
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of(
                        "issuer", keycloak.issuer(),
                        "end_session_endpoint", keycloak.publicEndpoint("logout")))
                .clientName("Keycloak")
                .build();
    }
}
