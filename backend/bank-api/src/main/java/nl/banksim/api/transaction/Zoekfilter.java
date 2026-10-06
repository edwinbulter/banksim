package nl.banksim.api.transaction;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.transactie.TransactieType;

/** Criteria uit het zoekformulier (FO §Zoeken in transacties); alles optioneel en samen (EN). */
record Zoekfilter(String tekst, Money min, Money max, TransactieType type, Richting richting) {

    enum Richting { ALL, OUT, IN }
}
