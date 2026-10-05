package nl.banksim.api.integratie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import nl.banksim.api.simulation.SimulationClock;

/**
 * Basis voor integratietests: echte PostgreSQL met de migraties uit bank-migrate (TO §13). De applicatie
 * verbindt als bank_app, zodat de minimale databaserechten meegetest worden; fixtures gaan via de superuser.
 * Eén container voor alle testklassen (singleton), zodat de Spring-context hergebruikt wordt.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegratieTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine").withInitScript("roles.sql");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected SimulationClock klok;

    /** Laadt de standaard testdata en vergeet de gecachete simulatiedatum. */
    protected void laadFixture() {
        Fixture.laad(beheer);
        klok.herlaad();
    }

    protected final JdbcTemplate beheer = new JdbcTemplate(
            new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&options=-c%20statement_timeout%3D5000");
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

    protected static JwtRequestPostProcessor klant(String sub, String gebruikersnaam, String naam) {
        return jwt().jwt(j -> j.subject(sub).claim("preferred_username", gebruikersnaam).claim("name", naam))
                .authorities(new SimpleGrantedAuthority("ROLE_klant"));
    }

    protected static JwtRequestPostProcessor jan() {
        return klant(Fixture.JAN_SUB, "jdevries", "Jan de Vries");
    }

    protected static JwtRequestPostProcessor piet() {
        return klant(Fixture.PIET_SUB, "ppieters", "Piet Pieters");
    }

    protected static JwtRequestPostProcessor beheerder() {
        return jwt().jwt(j -> j.subject("sub-beheerder").claim("preferred_username", "beheerder").claim("name", "BankSim Beheerder"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
