package nl.banksim.datagen.model;

import java.time.LocalDate;
import java.util.UUID;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;

public record Rekening(Iban iban, UUID rekeninghouderId, RekeningSoort soort, Money openingssaldo,
                       LocalDate geopendOp) {
}
