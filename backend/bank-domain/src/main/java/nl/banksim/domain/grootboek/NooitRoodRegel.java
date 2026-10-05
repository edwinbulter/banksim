package nl.banksim.domain.grootboek;

import java.util.Collection;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;

/**
 * Betaal- en spaarrekeningen staan nooit rood (FO). Omdat er al boekingen na de simulatiedatum kunnen bestaan,
 * telt niet alleen het saldo op die dag, maar het laagste eindsaldo vanaf die dag (TO §6).
 */
public final class NooitRoodRegel {

    private NooitRoodRegel() {
    }

    /**
     * @param saldoVoorBegindatum eindsaldo van de dag vóór de eerste datum die meetelt
     * @param mutatiesVanafBegindatum alle mutaties vanaf die datum, inclusief de nieuwe boeking
     * @throws SaldoOntoereikendException als het saldo op enige dag onder nul komt
     */
    public static void controleer(Iban rekening, RekeningSoort soort, Money saldoVoorBegindatum,
                                  Collection<Mutatie> mutatiesVanafBegindatum) {
        if (!soort.magNietRoodStaan()) {
            return;
        }
        if (SaldoVerloop.laagsteEindsaldo(saldoVoorBegindatum, mutatiesVanafBegindatum).isNegative()) {
            throw new SaldoOntoereikendException(rekening);
        }
    }
}
