package nl.banksim.domain.grootboek;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import nl.banksim.domain.geld.Money;

/** Berekeningen over het saldo van één rekening in de tijd. Saldo's worden nooit opgeslagen (TO §4). */
public final class SaldoVerloop {

    private SaldoVerloop() {
    }

    /** Saldo aan het eind van {@code datum}: beginsaldo plus alle mutaties tot en met die dag. */
    public static Money saldoOp(Money beginsaldo, Collection<Mutatie> mutaties, LocalDate datum) {
        Money saldo = beginsaldo;
        for (Mutatie mutatie : mutaties) {
            if (!mutatie.datum().isAfter(datum)) {
                saldo = saldo.plus(mutatie.bedrag());
            }
        }
        return saldo;
    }

    /**
     * Laagste eindsaldo van de dag over alle dagen met een mutatie, beginnend bij {@code beginsaldo}. Zonder
     * mutaties is dat het beginsaldo zelf. Volgorde binnen een dag telt niet mee: alleen eindsaldo's tellen.
     */
    public static Money laagsteEindsaldo(Money beginsaldo, Collection<Mutatie> mutaties) {
        Map<LocalDate, Money> perDag = new TreeMap<>();
        for (Mutatie mutatie : mutaties) {
            perDag.merge(mutatie.datum(), mutatie.bedrag(), Money::plus);
        }
        Money saldo = beginsaldo;
        Money laagste = beginsaldo;
        for (Money dagtotaal : perDag.values()) {
            saldo = saldo.plus(dagtotaal);
            laagste = Money.min(laagste, saldo);
        }
        return laagste;
    }
}
