package nl.banksim.datagen.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import nl.banksim.datagen.generator.Generator;
import nl.banksim.datagen.model.Dataset;
import nl.banksim.datagen.model.Rekeninghouder;
import nl.banksim.domain.geld.Money;

/** Schrijft de volledige dataset naar een echte PostgreSQL met het schema uit bank-migrate. */
@SpringBootTest(properties = {
        "banksim.datagen.uitvoeren=false",
        "spring.flyway.locations=filesystem:../bank-migrate/src/main/resources/db/migration"
})
@Testcontainers
class DatabaseSchrijverTests {

    static final LocalDate VANAF = LocalDate.of(2021, 10, 1);
    static final LocalDate TOT = LocalDate.of(2026, 12, 31);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine").withInitScript("roles.sql");

    static Dataset data;
    static Map<String, String> keycloakIds = new HashMap<>();

    @Autowired
    DatabaseSchrijver schrijver;

    @Autowired
    Invarianten invarianten;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    static void genereer() {
        data = new Generator(42, VANAF, TOT).genereer();
        data.rekeninghouders().stream().filter(Rekeninghouder::kanInloggen)
                .forEach(h -> keycloakIds.put(h.gebruikersnaam(), "kc-" + h.gebruikersnaam()));
    }

    @Test
    void schrijftAllesEnDeInvariantenKloppen() {
        schrijver.schrijf(data, keycloakIds, VANAF, TOT);

        assertThat(schrijver.heeftData()).isTrue();
        assertThat(aantal("overboeking")).isEqualTo(data.overboekingen().size());
        assertThat(aantal("boeking")).isEqualTo(2L * data.overboekingen().size());
        assertThat(aantal("contact")).isEqualTo(data.contacten().size());
        assertThat(jdbc.queryForObject("SELECT keycloak_sub FROM rekeninghouder WHERE naam = 'Jan de Vries'", String.class))
                .isEqualTo("kc-jdevries");
        assertThat(jdbc.queryForObject("SELECT simulatiedatum FROM instelling", LocalDate.class)).isNull();
        invarianten.controleer();

        // Saldo in de database = saldo volgens de generator
        Money verwacht = data.rekeningen().getFirst().openingssaldo();
        String iban = data.rekeningen().getFirst().iban().value();
        for (var o : data.overboekingen()) {
            for (var b : o.boekingen()) {
                if (b.rekening().value().equals(iban)) {
                    verwacht = verwacht.plus(b.bedrag());
                }
            }
        }
        BigDecimal inDatabase = jdbc.queryForObject("""
                SELECT r.openingssaldo + COALESCE(sum(b.bedrag), 0) FROM rekening r
                LEFT JOIN boeking b ON b.rekening_iban = r.iban WHERE r.iban = ? GROUP BY r.openingssaldo
                """, BigDecimal.class, iban);
        assertThat(Money.of(inDatabase)).isEqualTo(verwacht);
    }

    @Test
    void opnieuwSchrijvenVervangtAlleData() {
        schrijver.schrijf(data, keycloakIds, VANAF, TOT);
        jdbc.update("UPDATE instelling SET simulatiedatum = DATE '2025-01-01'");
        schrijver.schrijf(data, keycloakIds, VANAF, TOT);

        assertThat(aantal("overboeking")).isEqualTo(data.overboekingen().size());
        assertThat(jdbc.queryForObject("SELECT simulatiedatum FROM instelling", LocalDate.class)).isNull();
        assertThat(aantal("audit_log")).isEqualTo(1);
    }

    @Test
    void schrijftAlsDatagenGebruikerMetMinimaleRechten() {
        // De rechten uit V2 moeten genoeg zijn voor bank_datagen.
        jdbc.execute("SET ROLE bank_datagen");
        try {
            schrijver.schrijf(data, keycloakIds, VANAF, TOT);
        } finally {
            jdbc.execute("RESET ROLE");
        }
        assertThat(aantal("rekeninghouder")).isEqualTo(data.rekeninghouders().size());
    }

    private long aantal(String tabel) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabel, Long.class);
    }
}
