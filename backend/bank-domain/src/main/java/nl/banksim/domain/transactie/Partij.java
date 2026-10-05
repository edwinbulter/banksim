package nl.banksim.domain.transactie;

import java.util.Objects;

import nl.banksim.domain.rekening.Iban;

/** Een kant van een overboeking: rekening en naam van de rekeninghouder. */
public record Partij(Iban iban, String naam) {

    public static final int MAX_NAAM = 70;

    public Partij {
        Objects.requireNonNull(iban, "iban");
        Objects.requireNonNull(naam, "naam");
        if (naam.isBlank() || naam.length() > MAX_NAAM) {
            throw new IllegalArgumentException("Naam moet 1 tot 70 tekens zijn");
        }
    }
}
