package nl.banksim.api.transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.transactie.TransactieType;

record TransactieRij(UUID id, LocalDate boekdatum, Instant tijdstip, String tegenIban, String tegenNaam, Money bedrag,
                     TransactieType type, LocalDate uitvoerDatum, String omschrijving, String betalingskenmerk,
                     String extraOmschrijving) {
}
