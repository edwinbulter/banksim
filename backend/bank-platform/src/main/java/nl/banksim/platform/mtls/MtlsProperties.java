package nl.banksim.platform.mtls;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled        uit te zetten in tests zonder TLS
 * @param allowedClients CN's van clientcertificaten die deze service mogen aanroepen
 */
@ConfigurationProperties("banksim.mtls")
public record MtlsProperties(Boolean enabled, Set<String> allowedClients) {

    public MtlsProperties {
        enabled = enabled == null || enabled;
        allowedClients = allowedClients == null ? Set.of() : Set.copyOf(allowedClients);
    }
}
