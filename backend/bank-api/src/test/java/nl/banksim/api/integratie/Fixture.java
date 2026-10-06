package nl.banksim.api.integratie;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import nl.banksim.domain.rekening.Iban;

/**
 * Kleine, overzichtelijke testdata. Simulatiedatum D = 5 oktober 2026.
 * <ul>
 *   <li>Jan: betaal 3.000 begin, 60 incasso's van € 1,00 (1 juli – 29 aug), betaalautomaat € 25 (1 sep),
 *       en een toekomstige incasso van € 900 op 20 oktober. Saldo op D: € 2.915,00.</li>
 *   <li>Jan spaar: 5.000 begin + € 10,00 rente op 30 sep; toekomstige rente van € 10,00 per maand t/m december.</li>
 *   <li>Piet: betaal € 500,00.</li>
 * </ul>
 */
public final class Fixture {

    public static final LocalDate D = LocalDate.of(2026, 10, 5);
    public static final String JAN_SUB = "sub-jan";
    public static final String PIET_SUB = "sub-piet";
    public static final UUID JAN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID PIET = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    public static final UUID ENECO = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    public static final UUID BANK = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    public static final String JAN_BETAAL = Iban.nl("SIMB", 100011).value();
    public static final String JAN_SPAAR = Iban.nl("SIMB", 100012).value();
    public static final String PIET_BETAAL = Iban.nl("SIMB", 100021).value();
    public static final String PIET_SPAAR = Iban.nl("SIMB", 100022).value();
    public static final String ENECO_IBAN = Iban.nl("SIMB", 9_000_000_019L).value();
    public static final String RENTE_IBAN = Iban.nl("SIMB", 9_000_000_050L).value();

    private Fixture() {
    }

    /** In één transactie: de database weigert overboekingen die bij een commit niet in balans zijn. */
    public static void laad(JdbcTemplate db) {
        new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()))
                .executeWithoutResult(status -> vul(db));
    }

    private static void vul(JdbcTemplate db) {
        db.execute("TRUNCATE audit_log, idempotency_key, boeking, overboeking, contact, rekening, rekeninghouder");
        db.update("UPDATE instelling SET simulatiedatum = ?", D);
        houder(db, JAN, "Jan de Vries", "HUISHOUDEN", JAN_SUB);
        houder(db, PIET, "Piet Pieters", "HUISHOUDEN", PIET_SUB);
        houder(db, ENECO, "Eneco", "BEDRIJF", null);
        houder(db, BANK, "BankSim Rente", "BANK", null);
        rekening(db, JAN_BETAAL, JAN, "BETAAL", "3000.00");
        rekening(db, JAN_SPAAR, JAN, "SPAAR", "5000.00");
        rekening(db, PIET_BETAAL, PIET, "BETAAL", "500.00");
        rekening(db, PIET_SPAAR, PIET, "SPAAR", "0.00");
        rekening(db, ENECO_IBAN, ENECO, "EXTERN", "0.00");
        rekening(db, RENTE_IBAN, BANK, "EXTERN", "0.00");
        db.update("INSERT INTO contact (iban, naam, categorie) VALUES (?, 'Jan de Vries', 'particulier'), "
                + "(?, 'Piet Pieters', 'particulier'), (?, 'Eneco', 'energie')", JAN_BETAAL, PIET_BETAAL, ENECO_IBAN);

        LocalDate dag = LocalDate.of(2026, 7, 1);
        for (int i = 0; i < 60; i++) {
            boek(db, "INCASSO", JAN_BETAAL, "Jan de Vries", ENECO_IBAN, "Eneco", "1.00", dag.plusDays(i), "Termijn " + (i + 1), true);
        }
        boek(db, "BETAALAUTOMAAT", JAN_BETAAL, "Jan de Vries", ENECO_IBAN, "Eneco winkel Utrecht", "25.00",
                LocalDate.of(2026, 9, 1), "Betaalpas", false);
        boek(db, "INCASSO", JAN_BETAAL, "Jan de Vries", ENECO_IBAN, "Eneco", "900.00", LocalDate.of(2026, 10, 20),
                "Jaarafrekening", true);
        for (LocalDate einde : new LocalDate[] {LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31)}) {
            boek(db, "RENTE", RENTE_IBAN, "BankSim Rente", JAN_SPAAR, "Jan de Vries", "10.00", einde, "Rente", true);
        }
    }

    private static void houder(JdbcTemplate db, UUID id, String naam, String soort, String sub) {
        db.update("INSERT INTO rekeninghouder (id, naam, soort, keycloak_sub) VALUES (?, ?, ?, ?)", id, naam, soort, sub);
    }

    private static void rekening(JdbcTemplate db, String iban, UUID houder, String soort, String opening) {
        db.update("INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op) VALUES (?, ?, ?, ?, ?)",
                iban, houder, soort, new BigDecimal(opening), LocalDate.of(2021, 10, 1));
    }

    static void boek(JdbcTemplate db, String type, String van, String vanNaam, String naar, String naarNaam, String bedrag,
                     LocalDate datum, String omschrijving, boolean metIban) {
        UUID id = UUID.randomUUID();
        Timestamp tijd = Timestamp.valueOf(datum.atTime(LocalTime.of(10, 0)));
        db.update("INSERT INTO overboeking (id, type, transactie_tijdstip, uitvoer_datum, omschrijving) VALUES (?, ?, ?, ?, ?)",
                id, type, tijd, datum, omschrijving);
        String boeking = "INSERT INTO boeking (id, overboeking_id, rekening_iban, tegen_iban, tegen_naam, bedrag, boekdatum, "
                + "transactie_tijdstip) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        db.update(boeking, UUID.randomUUID(), id, van, metIban ? naar : null, naarNaam, new BigDecimal(bedrag).negate(), datum, tijd);
        db.update(boeking, UUID.randomUUID(), id, naar, metIban ? van : null, vanNaam, new BigDecimal(bedrag), datum, tijd);
    }
}
