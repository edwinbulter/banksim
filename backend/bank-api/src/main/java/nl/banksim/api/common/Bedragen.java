package nl.banksim.api.common;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;

/** Omzetting tussen de API (strings) en het domein. */
public final class Bedragen {

    private Bedragen() {
    }

    /** Leest een bedrag uit het contract ("12.50"); fout geeft 400. */
    public static Money lees(String bedrag) {
        try {
            return Money.of(bedrag);
        } catch (IllegalArgumentException e) {
            throw Fout.ongeldig("Ongeldig bedrag.");
        }
    }

    public static String tekst(Money bedrag) {
        return bedrag.toString();
    }

    /** IBAN uit een pad of veld; ongeldig wordt 404, zodat niet uitlekt of een rekening bestaat. */
    public static Iban rekening(String iban) {
        if (iban == null || !Iban.isGeldig(iban)) {
            throw Fout.nietGevonden("Rekening");
        }
        return Iban.of(iban);
    }
}
