package nl.banksim.bff;

import java.util.HashMap;
import java.util.Map;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gedeelde PostgreSQL voor BFF-tests, met de migraties uit bank-migrate. De BFF verbindt als bank_bff, zodat
 * de minimale rechten op schema bff meegetest worden.
 */
public final class BffTest {

    public static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6-alpine").withInitScript("roles.sql");
    public static final String SESSIE_SLEUTEL = "test-sessiesleutel-van-minstens-32-tekens";

    static {
        POSTGRES.start();
    }

    private BffTest() {
    }

    /** Eigenschappen voor een BFF zonder TLS tegen de testdatabase. */
    public static Map<String, Object> eigenschappen() {
        Map<String, Object> p = new HashMap<>();
        p.put("spring.datasource.url", POSTGRES.getJdbcUrl() + "&currentSchema=bff");
        p.put("spring.datasource.username", "bank_bff");
        p.put("spring.datasource.password", "bank_bff");
        p.put("spring.flyway.enabled", "true");
        p.put("spring.flyway.url", POSTGRES.getJdbcUrl());
        p.put("spring.flyway.user", POSTGRES.getUsername());
        p.put("spring.flyway.password", POSTGRES.getPassword());
        p.put("spring.flyway.locations", "filesystem:../bank-migrate/src/main/resources/db/migration");
        p.put("banksim.mtls.enabled", "false");
        p.put("server.ssl.enabled", "false");
        p.put("spring.ssl.bundle.pem.server.keystore.certificate", "");
        p.put("spring.ssl.bundle.pem.server.keystore.private-key", "");
        p.put("spring.ssl.bundle.pem.server.truststore.certificate", "");
        p.put("banksim.bff.sessie.sleutel", SESSIE_SLEUTEL);
        p.put("banksim.bff.keycloak.client-secret", "test-secret");
        return p;
    }

    public static void registreer(DynamicPropertyRegistry registry) {
        eigenschappen().forEach((k, v) -> registry.add(k, () -> v));
    }
}
