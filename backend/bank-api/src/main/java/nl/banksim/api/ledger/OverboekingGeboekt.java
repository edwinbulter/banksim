package nl.banksim.api.ledger;

import nl.banksim.domain.transactie.Overboeking;

/**
 * Gepubliceerd binnen de databasetransactie van een boeking, vóór de controle op "nooit rood". Luisteraars
 * (zoals de rentecorrectie) boeken in dezelfde transactie mee.
 */
public record OverboekingGeboekt(Overboeking overboeking) {
}
