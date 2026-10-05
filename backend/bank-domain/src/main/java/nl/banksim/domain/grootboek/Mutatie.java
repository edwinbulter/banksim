package nl.banksim.domain.grootboek;

import java.time.LocalDate;
import java.util.Objects;

import nl.banksim.domain.geld.Money;

/** Een bedrag dat op een datum bij (positief) of af (negatief) gaat op één rekening. */
public record Mutatie(LocalDate datum, Money bedrag) {

    public Mutatie {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(bedrag, "bedrag");
    }
}
