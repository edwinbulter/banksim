package nl.banksim.api.savings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import nl.banksim.api.account.LopendeRente;
import nl.banksim.api.ledger.LedgerService;
import nl.banksim.api.ledger.OverboekingGeboekt;
import nl.banksim.api.simulation.SimulationClock;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rente.RenteCalculator;
import nl.banksim.domain.transactie.Overboeking;
import nl.banksim.domain.transactie.Partij;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Spaarrente (TO §7): lopende rente voor het Spaarrekening-scherm, en rentecorrecties na elke inleg of
 * opname. Omdat de fake data al rente voor latere maanden bevat, wordt die na een wijziging van het saldo
 * opnieuw berekend; het verschil wordt als correctieboeking via het grootboek geboekt (niets wordt
 * verwijderd).
 */
@Service
class RenteService implements LopendeRente {

    static final String RENTEREKENING = "BankSim Rente";
    private static final DateTimeFormatter MAAND = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("nl"));
    private static final Logger log = LoggerFactory.getLogger(RenteService.class);

    private final JdbcClient jdbc;
    private final LedgerService ledger;
    private final SimulationClock klok;
    private final RenteCalculator calculator = RenteCalculator.standaard();

    RenteService(JdbcClient jdbc, LedgerService ledger, SimulationClock klok) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.klok = klok;
    }

    @Override
    public Money tot(Iban spaarrekening, LocalDate datum) {
        YearMonth maand = YearMonth.from(datum);
        if (datum.equals(maand.atEndOfMonth())) {
            return Money.ZERO; // de rente van deze maand is vandaag bijgeschreven
        }
        Map<LocalDate, Money> eindsaldi = eindsaldi(spaarrekening, maand.atDay(1), datum, null);
        return Money.afgerond(calculator.opgebouwd(maand.atDay(1), datum, eindsaldi::get));
    }

    @EventListener
    void herberekenNaBoeking(OverboekingGeboekt event) {
        Overboeking overboeking = event.overboeking();
        for (Partij partij : new Partij[] {overboeking.van(), overboeking.naar()}) {
            if (isSpaarrekening(partij.iban())) {
                herbereken(partij, overboeking.uitvoerDatum());
            }
        }
    }

    private void herbereken(Partij spaar, LocalDate vanaf) {
        LocalDate dataTot = klok.instelling().tot();
        Partij bank = renterekening();
        for (YearMonth maand = YearMonth.from(vanaf); !maand.atEndOfMonth().isAfter(dataTot); maand = maand.plusMonths(1)) {
            LocalDate einde = maand.atEndOfMonth();
            Map<LocalDate, Money> eindsaldi = eindsaldi(spaar.iban(), maand.atDay(1), einde, einde);
            Money nieuw = calculator.renteVoorMaand(maand, eindsaldi::get);
            Money bestaand = Money.of(jdbc.sql("""
                    SELECT COALESCE(sum(b.bedrag), 0) FROM boeking b JOIN overboeking o ON o.id = b.overboeking_id
                    WHERE b.rekening_iban = ? AND o.type = 'RENTE' AND b.boekdatum = ?
                    """).params(spaar.iban().value(), einde).query(BigDecimal.class).single());
            Money verschil = nieuw.minus(bestaand);
            if (!verschil.isZero()) {
                boolean bij = verschil.isPositive();
                ledger.boekCorrectie(new Overboeking(UUID.randomUUID(), TransactieType.RENTE,
                        bij ? bank : spaar, bij ? spaar : bank, verschil.abs(),
                        einde.atTime(LocalTime.of(23, 59, 30)).atZone(SimulationClock.ZONE).toInstant(), einde,
                        "Rentecorrectie " + MAAND.format(maand), null, null));
                log.info("Rentecorrectie {} voor {}: {}", maand, spaar.iban().gemaskeerd(), verschil);
            }
        }
    }

    /**
     * Eindsaldo per dag van {@code van} t/m {@code tot}. Met {@code zonderRenteOp} telt de rentebijschrijving
     * op die dag niet mee (rente wordt berekend over het saldo zonder de rente van die maand zelf).
     */
    private Map<LocalDate, Money> eindsaldi(Iban iban, LocalDate van, LocalDate tot, LocalDate zonderRenteOp) {
        Money saldo = Money.of(jdbc.sql("""
                SELECT r.openingssaldo + COALESCE((SELECT sum(b.bedrag) FROM boeking b
                                                   WHERE b.rekening_iban = r.iban AND b.boekdatum < ?), 0)
                FROM rekening r WHERE r.iban = ?
                """).params(van, iban.value()).query(BigDecimal.class).single());
        Map<LocalDate, Money> perDag = new HashMap<>();
        jdbc.sql("""
                SELECT b.boekdatum, sum(b.bedrag) FROM boeking b JOIN overboeking o ON o.id = b.overboeking_id
                WHERE b.rekening_iban = ? AND b.boekdatum BETWEEN ? AND ?
                  AND NOT (o.type = 'RENTE' AND b.boekdatum = COALESCE(?, DATE '0001-01-01'))
                GROUP BY b.boekdatum
                """).params(iban.value(), van, tot, zonderRenteOp)
                .query((rs, i) -> Map.entry(rs.getObject(1, LocalDate.class), Money.of(rs.getBigDecimal(2))))
                .list().forEach(e -> perDag.put(e.getKey(), e.getValue()));
        Map<LocalDate, Money> eindsaldi = new HashMap<>();
        for (LocalDate dag = van; !dag.isAfter(tot); dag = dag.plusDays(1)) {
            saldo = saldo.plus(perDag.getOrDefault(dag, Money.ZERO));
            eindsaldi.put(dag, saldo);
        }
        return eindsaldi;
    }

    private boolean isSpaarrekening(Iban iban) {
        return jdbc.sql("SELECT soort = 'SPAAR' FROM rekening WHERE iban = ?").param(iban.value())
                .query(Boolean.class).optional().orElse(false);
    }

    private Partij renterekening() {
        String iban = jdbc.sql("""
                SELECT r.iban FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                WHERE h.soort = 'BANK' AND h.naam = ?
                """).param(RENTEREKENING).query(String.class).single();
        return new Partij(Iban.of(iban), RENTEREKENING);
    }
}
