package nl.banksim.domain.transactie;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;

/**
 * Eén kant van een overboeking op één rekening. Negatief bedrag = afschrijving.
 *
 * @param tegenIban leeg bij betaalautomaat en geldautomaat (FO: IBAN mag dan ontbreken)
 */
public record Boeking(UUID overboekingId, Iban rekening, Iban tegenIban, String tegenNaam, Money bedrag,
                      LocalDate boekdatum, Instant transactieTijdstip) {

    public Boeking {
        Objects.requireNonNull(overboekingId, "overboekingId");
        Objects.requireNonNull(rekening, "rekening");
        Objects.requireNonNull(tegenNaam, "tegenNaam");
        Objects.requireNonNull(bedrag, "bedrag");
        Objects.requireNonNull(boekdatum, "boekdatum");
        Objects.requireNonNull(transactieTijdstip, "transactieTijdstip");
        if (bedrag.isZero()) {
            throw new IllegalArgumentException("Een boeking heeft een bedrag ongelijk aan nul");
        }
    }

    public boolean isAfschrijving() {
        return bedrag.isNegative();
    }
}
