package nl.banksim.datagen.model;

import java.util.List;

import nl.banksim.domain.transactie.Overboeking;

/** Alles wat de generator maakt; nog los van database en Keycloak. */
public record Dataset(List<Rekeninghouder> rekeninghouders, List<Rekening> rekeningen, List<Contact> contacten,
                      List<Overboeking> overboekingen) {

    public Dataset {
        rekeninghouders = List.copyOf(rekeninghouders);
        rekeningen = List.copyOf(rekeningen);
        contacten = List.copyOf(contacten);
        overboekingen = List.copyOf(overboekingen);
    }
}
