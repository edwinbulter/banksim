package nl.banksim.api.ledger;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.grootboek.Mutatie;
import nl.banksim.domain.grootboek.NooitRoodRegel;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.transactie.Boeking;
import nl.banksim.domain.transactie.Overboeking;

/**
 * De enige plek waar geld wordt geboekt (TO §6). Af- en bijschrijving gebeuren in dezelfde
 * databasetransactie: er wordt nooit afgeschreven zonder dat de doelrekening is bijgeschreven. Beide
 * rekeningen worden in vaste volgorde vergrendeld; de controle op "nooit rood" komt als laatste, na eventuele
 * rentecorrecties.
 */
@Service
public class LedgerService {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    LedgerService(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional(timeout = 5)
    public void boek(Overboeking overboeking) {
        Map<Iban, RekeningSoort> rekeningen = vergrendel(overboeking);
        schrijf(overboeking);
        events.publishEvent(new OverboekingGeboekt(overboeking));
        for (Map.Entry<Iban, RekeningSoort> rekening : rekeningen.entrySet()) {
            controleerNooitRood(rekening.getKey(), rekening.getValue(), overboeking.uitvoerDatum());
        }
    }

    /**
     * Boeking zonder event en zonder eigen controle, voor correcties binnen een lopende boeking (rente). De
     * controle op "nooit rood" van de omringende {@link #boek} dekt ook deze boeking.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void boekCorrectie(Overboeking overboeking) {
        vergrendel(overboeking);
        schrijf(overboeking);
    }

    /** Vergrendelt beide rekeningen in IBAN-volgorde, zodat A→B en B→A tegelijk geen deadlock geven. */
    private Map<Iban, RekeningSoort> vergrendel(Overboeking overboeking) {
        Map<Iban, RekeningSoort> rekeningen = jdbc.sql("""
                        SELECT iban, soort FROM rekening WHERE iban IN (?, ?) ORDER BY iban FOR UPDATE
                        """)
                .params(overboeking.van().iban().value(), overboeking.naar().iban().value())
                .query((rs, i) -> Map.entry(Iban.of(rs.getString(1)), RekeningSoort.valueOf(rs.getString(2))))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (rekeningen.size() != 2) {
            throw new IllegalStateException("Rekening bestaat niet");
        }
        return rekeningen;
    }

    private void schrijf(Overboeking o) {
        jdbc.sql("""
                INSERT INTO overboeking (id, type, transactie_tijdstip, uitvoer_datum, omschrijving, betalingskenmerk,
                                         extra_omschrijving)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(o.id(), o.type().name(), Timestamp.from(o.transactieTijdstip()), o.uitvoerDatum(),
                        o.omschrijving(), o.betalingskenmerk(), o.extraOmschrijving())
                .update();
        for (Boeking b : o.boekingen()) {
            jdbc.sql("""
                    INSERT INTO boeking (id, overboeking_id, rekening_iban, tegen_iban, tegen_naam, bedrag, boekdatum,
                                         transactie_tijdstip)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .params(UUID.randomUUID(), b.overboekingId(), b.rekening().value(),
                            b.tegenIban() == null ? null : b.tegenIban().value(), b.tegenNaam(), b.bedrag().amount(),
                            b.boekdatum(), Timestamp.from(b.transactieTijdstip()))
                    .update();
        }
    }

    /** Het laagste eindsaldo vanaf de boekdatum, inclusief al bestaande latere boekingen, moet ≥ 0 zijn. */
    private void controleerNooitRood(Iban iban, RekeningSoort soort, LocalDate vanaf) {
        if (!soort.magNietRoodStaan()) {
            return;
        }
        BigDecimal saldoVoor = jdbc.sql("""
                SELECT r.openingssaldo + COALESCE((SELECT sum(b.bedrag) FROM boeking b
                                                   WHERE b.rekening_iban = r.iban AND b.boekdatum < ?), 0)
                FROM rekening r WHERE r.iban = ?
                """).params(vanaf, iban.value()).query(BigDecimal.class).single();
        List<Mutatie> mutaties = jdbc.sql("""
                SELECT boekdatum, sum(bedrag) FROM boeking WHERE rekening_iban = ? AND boekdatum >= ?
                GROUP BY boekdatum
                """).params(iban.value(), vanaf)
                .query((rs, i) -> new Mutatie(rs.getObject(1, LocalDate.class), Money.of(rs.getBigDecimal(2))))
                .list();
        NooitRoodRegel.controleer(iban, soort, Money.of(saldoVoor), mutaties);
    }
}
