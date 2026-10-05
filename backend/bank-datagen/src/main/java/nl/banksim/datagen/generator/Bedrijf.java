package nl.banksim.datagen.generator;

import java.util.UUID;

import nl.banksim.domain.rekening.Iban;

/** Een fictief bedrijf of een interne rekening van BankSim, met een vaste IBAN. */
public record Bedrijf(String sleutel, String naam, String categorie, Iban iban) {

    public UUID rekeninghouderId() {
        return Ids.uuid("bedrijf:" + sleutel);
    }
}
