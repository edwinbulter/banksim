package nl.banksim.bff.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Zet de Keycloak-realmrollen uit het ID-token ({@code realm_access.roles}) om naar {@code ROLE_<rol>}. De BFF
 * gebruikt ze alleen om na het inloggen naar de juiste startpagina te sturen; autorisatie gebeurt in bank-api.
 */
final class Rollen implements GrantedAuthoritiesMapper {

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> resultaat = new HashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc
                    && oidc.getIdToken().getClaims().get("realm_access") instanceof Map<?, ?> realmAccess
                    && realmAccess.get("roles") instanceof Collection<?> rollen) {
                rollen.forEach(rol -> resultaat.add(new SimpleGrantedAuthority("ROLE_" + rol)));
            }
        }
        return resultaat;
    }
}
