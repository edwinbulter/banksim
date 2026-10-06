package nl.banksim.datagen.generator;

import java.time.LocalTime;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.transactie.Partij;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Een geplande geldbeweging op een dag. De generator boekt hem alleen als de rekening niet rood komt te staan.
 *
 * @param vast        vaste last: bij een tekort eerst geld van de spaarrekening halen
 * @param buffer      alleen boeken als er daarna nog minstens dit bedrag op de rekening staat
 * @param afromenTot  sparen: maak het bedrag zo groot dat er niet meer dan dit op de rekening blijft
 */
public record Gebeurtenis(LocalTime tijd, TransactieType type, Partij van, Partij naar, Money bedrag,
                          String omschrijving, String betalingskenmerk, String extraOmschrijving, boolean vast,
                          Money buffer, Money afromenTot) {

    public static Gebeurtenis van(LocalTime tijd, TransactieType type, Partij van, Partij naar, Money bedrag,
                                  String omschrijving) {
        return new Gebeurtenis(tijd, type, van, naar, bedrag, omschrijving, null, null, false, Money.ZERO, null);
    }

    public Gebeurtenis alsVasteLast() {
        return new Gebeurtenis(tijd, type, van, naar, bedrag, omschrijving, betalingskenmerk, extraOmschrijving,
                true, buffer, afromenTot);
    }

    public Gebeurtenis metKenmerk(String kenmerk) {
        return new Gebeurtenis(tijd, type, van, naar, bedrag, omschrijving, kenmerk, extraOmschrijving, vast, buffer, afromenTot);
    }

    public Gebeurtenis metExtra(String extra) {
        return new Gebeurtenis(tijd, type, van, naar, bedrag, omschrijving, betalingskenmerk, extra, vast, buffer, afromenTot);
    }

    public Gebeurtenis metBuffer(Money minimaalSaldoNa) {
        return new Gebeurtenis(tijd, type, van, naar, bedrag, omschrijving, betalingskenmerk, extraOmschrijving,
                vast, minimaalSaldoNa, afromenTot);
    }

    public Gebeurtenis afromenTot(Money doelsaldo) {
        return new Gebeurtenis(tijd, type, van, naar, bedrag, omschrijving, betalingskenmerk, extraOmschrijving,
                vast, buffer, doelsaldo);
    }
}
