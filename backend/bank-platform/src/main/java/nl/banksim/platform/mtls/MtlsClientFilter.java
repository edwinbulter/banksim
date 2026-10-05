package nl.banksim.platform.mtls;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Laat alleen aanroepers toe waarvan het clientcertificaat (al gecontroleerd door TLS tegen de BankSim-CA)
 * een toegestane CN heeft, bijvoorbeeld alleen bank-bff voor bank-api (zero trust, TO §10.1).
 */
public class MtlsClientFilter extends OncePerRequestFilter {

    static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private static final Logger log = LoggerFactory.getLogger(MtlsClientFilter.class);

    private final Set<String> allowedClients;

    public MtlsClientFilter(Set<String> allowedClients) {
        this.allowedClients = Set.copyOf(allowedClients);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> client = clientCommonName(request);
        if (client.isPresent() && allowedClients.contains(client.get())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Aanroep geweigerd: clientcertificaat {} is niet toegestaan", client.orElse("(geen)"));
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    static Optional<String> clientCommonName(HttpServletRequest request) {
        if (!(request.getAttribute(CERTIFICATE_ATTRIBUTE) instanceof X509Certificate[] chain) || chain.length == 0) {
            return Optional.empty();
        }
        try {
            LdapName name = new LdapName(chain[0].getSubjectX500Principal().getName());
            return name.getRdns().stream()
                    .filter(rdn -> "CN".equalsIgnoreCase(rdn.getType()))
                    .map(Rdn::getValue)
                    .map(String::valueOf)
                    .findFirst();
        } catch (InvalidNameException e) {
            return Optional.empty();
        }
    }
}
