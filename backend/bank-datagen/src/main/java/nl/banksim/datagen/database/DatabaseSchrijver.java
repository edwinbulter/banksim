package nl.banksim.datagen.database;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import nl.banksim.datagen.generator.Ids;
import nl.banksim.datagen.model.Contact;
import nl.banksim.datagen.model.Dataset;
import nl.banksim.datagen.model.Rekening;
import nl.banksim.datagen.model.Rekeninghouder;
import nl.banksim.domain.transactie.Boeking;
import nl.banksim.domain.transactie.Overboeking;

/**
 * Vervangt alle bankdata in één databasetransactie, met PostgreSQL {@code COPY} voor snelheid. De
 * balanscontrole in de database (deferred trigger) controleert bij de commit elke overboeking.
 */
@Component
public class DatabaseSchrijver {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchrijver.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DatabaseSchrijver(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    public boolean heeftData() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM rekeninghouder)", Boolean.class));
    }

    @Transactional
    public void schrijf(Dataset data, Map<String, String> keycloakIds, LocalDate vanaf, LocalDate tot) {
        jdbc.execute("TRUNCATE audit_log, idempotency_key, boeking, overboeking, contact, rekening, rekeninghouder");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            CopyManager copy = connection.unwrap(PGConnection.class).getCopyAPI();
            kopieer(copy, "rekeninghouder (id, naam, soort, keycloak_sub)", rekeninghouders(data, keycloakIds));
            kopieer(copy, "rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)", rekeningen(data));
            kopieer(copy, "contact (iban, naam, categorie)", contacten(data));
            kopieer(copy, "overboeking (id, type, transactie_tijdstip, uitvoer_datum, omschrijving, betalingskenmerk, extra_omschrijving)",
                    overboekingen(data));
            kopieer(copy, "boeking (id, overboeking_id, rekening_iban, tegen_iban, tegen_naam, bedrag, boekdatum, transactie_tijdstip)",
                    boekingen(data));
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Schrijven naar de database mislukt", e);
        }
        jdbc.update("UPDATE instelling SET simulatiedatum = NULL, data_vanaf = ?, data_tot = ?", vanaf, tot);
        jdbc.update("INSERT INTO audit_log (actor, actie, details) VALUES ('bank-datagen', 'DATA_GEGENEREERD', ?::jsonb)",
                "{\"overboekingen\": %d, \"rekeningen\": %d}".formatted(data.overboekingen().size(), data.rekeningen().size()));
        log.info("{} rekeninghouders, {} rekeningen, {} contacten en {} overboekingen geschreven",
                data.rekeninghouders().size(), data.rekeningen().size(), data.contacten().size(), data.overboekingen().size());
    }

    private static void kopieer(CopyManager copy, String tabel, String csv) throws SQLException, IOException {
        copy.copyIn("COPY " + tabel + " FROM STDIN WITH (FORMAT csv)", new StringReader(csv));
    }

    private static String rekeninghouders(Dataset data, Map<String, String> keycloakIds) {
        StringBuilder csv = new StringBuilder();
        for (Rekeninghouder h : data.rekeninghouders()) {
            String sub = h.kanInloggen() ? keycloakIds.get(h.gebruikersnaam()) : null;
            if (h.kanInloggen() && sub == null) {
                throw new IllegalStateException("Geen Keycloak-id voor " + h.gebruikersnaam());
            }
            regel(csv, h.id(), h.naam(), h.soort().name(), sub);
        }
        return csv.toString();
    }

    private static String rekeningen(Dataset data) {
        StringBuilder csv = new StringBuilder();
        for (Rekening r : data.rekeningen()) {
            regel(csv, r.iban().value(), r.rekeninghouderId(), r.soort().name(), r.openingssaldo(), r.geopendOp());
        }
        return csv.toString();
    }

    private static String contacten(Dataset data) {
        StringBuilder csv = new StringBuilder();
        for (Contact c : data.contacten()) {
            regel(csv, c.iban().value(), c.naam(), c.categorie());
        }
        return csv.toString();
    }

    private static String overboekingen(Dataset data) {
        StringBuilder csv = new StringBuilder();
        for (Overboeking o : data.overboekingen()) {
            regel(csv, o.id(), o.type().name(), o.transactieTijdstip(), o.uitvoerDatum(), o.omschrijving(),
                    o.betalingskenmerk(), o.extraOmschrijving());
        }
        return csv.toString();
    }

    private static String boekingen(Dataset data) {
        StringBuilder csv = new StringBuilder();
        for (Overboeking o : data.overboekingen()) {
            int i = 0;
            for (Boeking b : o.boekingen()) {
                UUID id = Ids.uuid(o.id() + ":" + i++);
                regel(csv, id, b.overboekingId(), b.rekening().value(), b.tegenIban() == null ? null : b.tegenIban().value(),
                        b.tegenNaam(), b.bedrag(), b.boekdatum(), b.transactieTijdstip());
            }
        }
        return csv.toString();
    }

    /** Eén CSV-regel; {@code null} wordt een leeg veld (NULL), tekst altijd tussen aanhalingstekens. */
    private static void regel(StringBuilder csv, Object... velden) {
        for (int i = 0; i < velden.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            Object veld = velden[i];
            if (veld == null) {
                continue;
            }
            String tekst = veld instanceof Instant instant ? instant.toString() : veld.toString();
            csv.append('"').append(tekst.replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }
}
