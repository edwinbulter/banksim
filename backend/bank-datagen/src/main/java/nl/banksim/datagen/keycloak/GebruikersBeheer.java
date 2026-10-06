package nl.banksim.datagen.keycloak;

import java.util.List;
import java.util.Map;

import nl.banksim.datagen.model.Rekeninghouder;

/** Zorgt dat elke klant en de beheerder in Keycloak bestaan, met het juiste wachtwoord en de juiste rol. */
public interface GebruikersBeheer {

    /** @return gebruikersnaam → Keycloak-id ({@code sub}) van elke klant */
    Map<String, String> zorgVoorKlanten(List<Rekeninghouder> klanten);

    void zorgVoorBeheerder();
}
