package nl.banksim.api.account;

import java.util.UUID;

import nl.banksim.domain.rekening.Iban;

/** Een huishouden met zijn betaal- en spaarrekening (beheerdersoverzicht). */
public record Huishouden(UUID id, String naam, Iban betaal, Iban spaar) {
}
