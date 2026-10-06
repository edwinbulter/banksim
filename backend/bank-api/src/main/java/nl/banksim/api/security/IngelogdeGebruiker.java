package nl.banksim.api.security;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** De gebruiker van de huidige request, uit het gevalideerde JWT. */
@Component
public class IngelogdeGebruiker {

    public static final String KLANT = "klant";
    public static final String ADMIN = "admin";

    public String sub() {
        return jwt().getSubject();
    }

    public String gebruikersnaam() {
        return jwt().getClaimAsString("preferred_username");
    }

    public String naam() {
        String naam = jwt().getClaimAsString("name");
        return naam != null ? naam : gebruikersnaam();
    }

    public boolean isKlant() {
        return rollen().contains(KLANT);
    }

    public boolean isAdmin() {
        return rollen().contains(ADMIN);
    }

    public List<String> rollen() {
        return authentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("Geen ingelogde gebruiker");
        }
        return authentication;
    }

    private static Jwt jwt() {
        if (!(authentication().getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Geen JWT");
        }
        return jwt;
    }
}
