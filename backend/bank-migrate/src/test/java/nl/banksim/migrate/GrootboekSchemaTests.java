package nl.banksim.migrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Het schema zelf bewaakt de dubbele boekhouding en de minimale rechten (TO §4, §10.1). */
@SpringBootTest
@Testcontainers
class GrootboekSchemaTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine")
            .withInitScript("roles.sql");

    static final String JAN = "NL07SIMB0000000001";
    static final String ENECO = "NL61SIMB0000009001";
    static final String SPAAR = "NL80SIMB0000000002";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void rekeningen() {
        jdbc.execute("TRUNCATE boeking, overboeking, contact, rekening, rekeninghouder CASCADE");
        UUID huishouden = UUID.randomUUID();
        UUID bedrijf = UUID.randomUUID();
        jdbc.update("INSERT INTO rekeninghouder (id, naam, soort, keycloak_sub) VALUES (?, 'J. de Vries', 'HUISHOUDEN', 'sub-1')", huishouden);
        jdbc.update("INSERT INTO rekeninghouder (id, naam, soort) VALUES (?, 'Eneco', 'BEDRIJF')", bedrijf);
        for (String[] r : new String[][] {{JAN, "BETAAL"}, {SPAAR, "SPAAR"}}) {
            jdbc.update("INSERT INTO rekening (iban, rekeninghouder_id, soort, geopend_op) VALUES (?, ?, ?, DATE '2021-10-01')",
                    r[0], huishouden, r[1]);
        }
        jdbc.update("INSERT INTO rekening (iban, rekeninghouder_id, soort, geopend_op) VALUES (?, ?, 'EXTERN', DATE '2021-10-01')",
                ENECO, bedrijf);
    }

    @Test
    void overboekingMetTweeBoekingenInBalansWordtOpgeslagen() {
        tx.executeWithoutResult(status -> {
            UUID id = overboeking();
            boeking(id, JAN, "-148.00");
            boeking(id, ENECO, "148.00");
        });
        assertThat(jdbc.queryForObject("SELECT sum(bedrag) FROM boeking", BigDecimal.class)).isZero();
    }

    @Test
    void halveOverboekingWordtBijCommitGeweigerd() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> boeking(overboeking(), JAN, "-148.00")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("niet in balans");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM boeking", Integer.class)).isZero();
    }

    @Test
    void boekingenDieNietOpNulUitkomenWordenGeweigerd() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID id = overboeking();
            boeking(id, JAN, "-148.00");
            boeking(id, ENECO, "140.00");
        })).hasMessageContaining("niet in balans");
    }

    @Test
    void tweeBoekingenOpDezelfdeRekeningWordenGeweigerd() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID id = overboeking();
            boeking(id, JAN, "-10.00");
            boeking(id, JAN, "10.00");
        })).hasMessageContaining("niet in balans");
    }

    @Test
    void derdeBoekingWordtGeweigerd() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID id = overboeking();
            boeking(id, JAN, "-10.00");
            boeking(id, ENECO, "5.00");
            boeking(id, SPAAR, "5.00");
        })).hasMessageContaining("niet in balans");
    }

    @Test
    void boekingVanNulIsOngeldig() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> boeking(overboeking(), JAN, "0.00")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void bankAppHeeftAlleenDeNodigeRechten() {
        assertThat(privilege("bank_app", "boeking", "INSERT")).isTrue();
        assertThat(privilege("bank_app", "boeking", "UPDATE")).isFalse();
        assertThat(privilege("bank_app", "boeking", "DELETE")).isFalse();
        assertThat(privilege("bank_app", "audit_log", "INSERT")).isTrue();
        assertThat(privilege("bank_app", "audit_log", "UPDATE")).isFalse();
        assertThat(privilege("bank_app", "audit_log", "DELETE")).isFalse();
        assertThat(privilege("bank_app", "rekening", "DELETE")).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_column_privilege('bank_app', 'rekening', 'versie', 'UPDATE')",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT has_column_privilege('bank_app', 'rekening', 'openingssaldo', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(privilege("bank_bff", "boeking", "SELECT")).isFalse();
    }

    @Test
    void bankAppKanRekeningVergrendelen() {
        tx.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL ROLE bank_app");
            assertThat(jdbc.queryForList("SELECT iban FROM rekening WHERE iban IN (?, ?) ORDER BY iban FOR UPDATE",
                    String.class, JAN, ENECO)).hasSize(2);
        });
    }

    @Test
    void simulatiedatumBlijftBinnenHetBereik() {
        jdbc.update("UPDATE instelling SET simulatiedatum = DATE '2026-10-05'");
        assertThatThrownBy(() -> jdbc.update("UPDATE instelling SET simulatiedatum = DATE '2027-01-01'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO instelling (id, data_vanaf, data_tot) VALUES (2, now(), now())"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void alleenHuishoudensKunnenInloggen() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO rekeninghouder (id, naam, soort, keycloak_sub) VALUES (?, 'AH', 'BEDRIJF', 'sub-x')",
                UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }

    private boolean privilege(String rol, String tabel, String recht) {
        return jdbc.queryForObject("SELECT has_table_privilege(?, ?, ?)", Boolean.class, rol, tabel, recht);
    }

    private UUID overboeking() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO overboeking (id, type, transactie_tijdstip, uitvoer_datum, omschrijving) VALUES (?, 'INCASSO', ?, ?, 'Energie')",
                id, Timestamp.from(Instant.parse("2026-10-01T04:02:00Z")), Date.valueOf(LocalDate.of(2026, 10, 1)));
        return id;
    }

    private void boeking(UUID overboeking, String rekening, String bedrag) {
        jdbc.update("""
                INSERT INTO boeking (id, overboeking_id, rekening_iban, tegen_iban, tegen_naam, bedrag, boekdatum, transactie_tijdstip)
                VALUES (?, ?, ?, NULL, 'Tegenpartij', ?, DATE '2026-10-01', TIMESTAMPTZ '2026-10-01 06:02:00+02')
                """, UUID.randomUUID(), overboeking, rekening, new BigDecimal(bedrag));
    }
}
