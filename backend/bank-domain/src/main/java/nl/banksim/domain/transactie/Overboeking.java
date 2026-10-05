package nl.banksim.domain.transactie;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import nl.banksim.domain.geld.Money;

/**
 * Elke geldbeweging in BankSim: van één rekening naar een andere, altijd als twee boekingen die samen nul
 * zijn (dubbel boekhouden, TO §6). Er is geen manier om alleen af of alleen bij te schrijven.
 */
public record Overboeking(UUID id, TransactieType type, Partij van, Partij naar, Money bedrag,
                          Instant transactieTijdstip, LocalDate uitvoerDatum,
                          String omschrijving, String betalingskenmerk, String extraOmschrijving) {

    public static final int MAX_OMSCHRIJVING = 140;
    public static final int MAX_BETALINGSKENMERK = 25;
    public static final int MAX_EXTRA_OMSCHRIJVING = 35;

    public Overboeking {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(van, "van");
        Objects.requireNonNull(naar, "naar");
        Objects.requireNonNull(bedrag, "bedrag");
        Objects.requireNonNull(transactieTijdstip, "transactieTijdstip");
        Objects.requireNonNull(uitvoerDatum, "uitvoerDatum");
        if (!bedrag.isPositive()) {
            throw new IllegalArgumentException("Het bedrag van een overboeking is groter dan nul");
        }
        if (van.iban().equals(naar.iban())) {
            throw new IllegalArgumentException("Van- en naar-rekening zijn verschillend");
        }
        omschrijving = maxLengte(omschrijving, MAX_OMSCHRIJVING, "Omschrijving");
        betalingskenmerk = maxLengte(betalingskenmerk, MAX_BETALINGSKENMERK, "Betalingskenmerk");
        extraOmschrijving = maxLengte(extraOmschrijving, MAX_EXTRA_OMSCHRIJVING, "Extra omschrijving");
    }

    /** De afschrijving bij {@code van} en de bijschrijving bij {@code naar}; samen precies nul. */
    public List<Boeking> boekingen() {
        boolean zonderTegenIban = type == TransactieType.BETAALAUTOMAAT || type == TransactieType.GELDAUTOMAAT;
        Boeking af = new Boeking(id, van.iban(), zonderTegenIban ? null : naar.iban(), naar.naam(),
                bedrag.negate(), uitvoerDatum, transactieTijdstip);
        Boeking bij = new Boeking(id, naar.iban(), zonderTegenIban ? null : van.iban(), van.naam(),
                bedrag, uitvoerDatum, transactieTijdstip);
        return List.of(af, bij);
    }

    private static String maxLengte(String waarde, int max, String veld) {
        if (waarde == null || waarde.isBlank()) {
            return null;
        }
        if (waarde.length() > max) {
            throw new IllegalArgumentException(veld + " is maximaal " + max + " tekens");
        }
        return waarde;
    }
}
