package nl.banksim.datagen.model;

import nl.banksim.domain.rekening.Iban;

/** Een rekening waarnaar klanten mogen betalen (FO: alleen bestaande rekeningen uit de fake data). */
public record Contact(Iban iban, String naam, String categorie) {
}
