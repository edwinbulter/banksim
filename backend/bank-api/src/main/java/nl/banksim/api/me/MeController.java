package nl.banksim.api.me;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class MeController {

    @GetMapping
    @PreAuthorize("hasAnyRole('klant', 'admin')")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> rollen = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .toList();
        String naam = jwt.getClaimAsString("name");
        String gebruikersnaam = jwt.getClaimAsString("preferred_username");
        return new MeResponse(naam != null ? naam : gebruikersnaam, gebruikersnaam, rollen);
    }

    record MeResponse(String naam, String gebruikersnaam, List<String> rollen) {
    }
}
