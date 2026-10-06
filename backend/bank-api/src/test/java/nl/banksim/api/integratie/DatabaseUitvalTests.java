package nl.banksim.api.integratie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

import nl.banksim.api.simulation.SimulationClock;

/**
 * TO §12: valt de database weg, dan geeft de API een nette 503 met Retry-After (geen 500, geen hangende
 * request) en herstelt hij vanzelf zodra de database terug is.
 */
@SpringBootTest(properties = "spring.datasource.hikari.connection-timeout=1000")
@AutoConfigureMockMvc
class DatabaseUitvalTests {

    static final Network NETWERK = Network.newNetwork();
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withInitScript("roles.sql").withNetwork(NETWERK).withNetworkAliases("postgres");
    static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0").withNetwork(NETWERK);
    static Proxy proxy;

    static {
        POSTGRES.start();
        TOXIPROXY.start();
        try {
            proxy = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort())
                    .createProxy("postgres", "0.0.0.0:8666", "postgres:5432");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    SimulationClock klok;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String viaProxy = "jdbc:postgresql://%s:%d/%s?options=-c%%20statement_timeout%%3D5000&connectTimeout=1&socketTimeout=3"
                .formatted(TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(8666), POSTGRES.getDatabaseName());
        registry.add("spring.datasource.url", () -> viaProxy);
        registry.add("spring.datasource.username", () -> "bank_app");
        registry.add("spring.datasource.password", () -> "bank_app");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "filesystem:../bank-migrate/src/main/resources/db/migration");
        registry.add("banksim.mtls.enabled", () -> "false");
        registry.add("server.ssl.enabled", () -> "false");
        registry.add("spring.ssl.bundle.pem.server.keystore.certificate", () -> "");
        registry.add("spring.ssl.bundle.pem.server.keystore.private-key", () -> "");
        registry.add("spring.ssl.bundle.pem.server.truststore.certificate", () -> "");
        registry.add("banksim.cursor-sleutel", () -> "test-sleutel-van-minstens-32-tekens!!");
    }

    @BeforeEach
    void data() throws IOException {
        proxy.enable();
        Fixture.laad(new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())));
        klok.herlaad();
    }

    @AfterEach
    void herstel() throws IOException {
        proxy.enable();
    }

    @Test
    void databaseWegGeeft503EnHerstelt() throws Exception {
        mvc.perform(get("/api/me/accounts").with(jan())).andExpect(status().isOk());

        proxy.disable();
        klok.herlaad();
        mvc.perform(get("/api/me/accounts").with(jan()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/tijdelijk-niet-beschikbaar"))
                .andExpect(jsonPath("$.detail").value("De bank is even niet bereikbaar. Probeer het over een paar seconden opnieuw."));

        proxy.enable();
        mvc.perform(get("/api/me/accounts").with(jan())).andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jan() {
        return jwt().jwt(j -> j.subject(Fixture.JAN_SUB).claim("preferred_username", "jdevries"))
                .authorities(new SimpleGrantedAuthority("ROLE_klant"));
    }
}
