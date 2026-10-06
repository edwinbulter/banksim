package nl.banksim.domain.rente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Function;

import nl.banksim.domain.geld.Money;

/**
 * Spaarrente: vast percentage per jaar, per dag berekend over het eindsaldo van die dag (actual/365 of 366)
 * en per maand afgerond bijgeschreven (TO §7). Tussenresultaten op 10 decimalen.
 */
public final class RenteCalculator {

    public static final BigDecimal STANDAARD_PERCENTAGE = new BigDecimal("0.03");
    private static final int TUSSEN_SCALE = 10;

    private final BigDecimal jaarpercentage;

    public RenteCalculator(BigDecimal jaarpercentage) {
        if (jaarpercentage.signum() < 0) {
            throw new IllegalArgumentException("Rente kan niet negatief zijn");
        }
        this.jaarpercentage = jaarpercentage;
    }

    public static RenteCalculator standaard() {
        return new RenteCalculator(STANDAARD_PERCENTAGE);
    }

    /** Rente over één dag; een saldo van nul of lager levert geen rente op. */
    public BigDecimal dagrente(Money eindsaldo, LocalDate dag) {
        if (!eindsaldo.isPositive()) {
            return BigDecimal.ZERO.setScale(TUSSEN_SCALE);
        }
        return eindsaldo.amount().multiply(jaarpercentage)
                .divide(BigDecimal.valueOf(dag.lengthOfYear()), TUSSEN_SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Opgebouwde, nog niet afgeronde rente van {@code van} tot en met {@code totEnMet}.
     *
     * @param eindsaldo eindsaldo per dag, zonder de rentebijschrijving van de maand waarover gerekend wordt
     */
    public BigDecimal opgebouwd(LocalDate van, LocalDate totEnMet, Function<LocalDate, Money> eindsaldo) {
        BigDecimal totaal = BigDecimal.ZERO.setScale(TUSSEN_SCALE);
        for (LocalDate dag = van; !dag.isAfter(totEnMet); dag = dag.plusDays(1)) {
            totaal = totaal.add(dagrente(eindsaldo.apply(dag), dag));
        }
        return totaal;
    }

    /** Rente over een hele maand, afgerond op centen; wordt op de laatste dag van de maand bijgeschreven. */
    public Money renteVoorMaand(YearMonth maand, Function<LocalDate, Money> eindsaldo) {
        return Money.afgerond(opgebouwd(maand.atDay(1), maand.atEndOfMonth(), eindsaldo));
    }
}
