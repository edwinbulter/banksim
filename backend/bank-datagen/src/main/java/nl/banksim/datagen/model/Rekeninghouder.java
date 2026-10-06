package nl.banksim.datagen.model;

import java.util.UUID;

/**
 * @param gebruikersnaam alleen voor huishoudens: inlognaam in Keycloak
 */
public record Rekeninghouder(UUID id, String naam, Soort soort, String gebruikersnaam, String voornaam,
                             String achternaam) {

    public enum Soort { HUISHOUDEN, BEDRIJF, BANK }

    public boolean kanInloggen() {
        return gebruikersnaam != null;
    }
}
