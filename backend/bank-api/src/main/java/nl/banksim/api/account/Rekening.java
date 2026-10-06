package nl.banksim.api.account;

import java.util.UUID;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;

/** Een rekening met zijn houder, zoals de API hem nodig heeft. */
public record Rekening(Iban iban, RekeningSoort soort, Money openingssaldo, UUID houderId, String houderNaam,
                       String houderSoort, String houderSub) {

    public boolean isVanHuishouden() {
        return "HUISHOUDEN".equals(houderSoort);
    }
}
