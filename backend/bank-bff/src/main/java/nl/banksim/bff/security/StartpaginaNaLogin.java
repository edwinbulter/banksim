package nl.banksim.bff.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/** Na het inloggen: beheerder naar {@code /admin}, klant naar {@code /} (FO: één inlogscherm). */
final class StartpaginaNaLogin implements AuthenticationSuccessHandler {

    private final String publicUrl;

    StartpaginaNaLogin(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        response.sendRedirect(publicUrl + startpagina(authentication));
    }

    static String startpagina(Authentication authentication) {
        boolean beheerder = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
        return beheerder ? "/admin" : "/";
    }
}
