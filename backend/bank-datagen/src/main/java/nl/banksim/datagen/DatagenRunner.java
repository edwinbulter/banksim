package nl.banksim.datagen;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import nl.banksim.datagen.database.DatabaseSchrijver;
import nl.banksim.datagen.database.Invarianten;
import nl.banksim.datagen.generator.Generator;
import nl.banksim.datagen.keycloak.GebruikersBeheer;
import nl.banksim.datagen.model.Dataset;
import nl.banksim.datagen.model.Rekeninghouder;

/** Genereert de data, maakt de Keycloak-gebruikers en schrijft alles naar de database. */
@Component
@ConditionalOnBooleanProperty(name = "banksim.datagen.uitvoeren", matchIfMissing = true)
class DatagenRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatagenRunner.class);

    private final DatagenProperties properties;
    private final GebruikersBeheer gebruikers;
    private final DatabaseSchrijver database;
    private final Invarianten invarianten;

    DatagenRunner(DatagenProperties properties, GebruikersBeheer gebruikers, DatabaseSchrijver database,
                  Invarianten invarianten) {
        this.properties = properties;
        this.gebruikers = gebruikers;
        this.database = database;
        this.invarianten = invarianten;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.modus() == DatagenProperties.Modus.ALS_LEEG && database.heeftData()) {
            log.info("Database bevat al data; niets gedaan (modus ALS_LEEG)");
            return;
        }
        Generator generator = new Generator(properties.seed(), properties.vanaf(), properties.tot());
        Dataset data = generator.genereer();
        log.info("{} overboekingen gegenereerd met seed {} ({} overgeslagen wegens saldo)",
                data.overboekingen().size(), properties.seed(), generator.overgeslagen());

        List<Rekeninghouder> klanten = data.rekeninghouders().stream().filter(Rekeninghouder::kanInloggen).toList();
        Map<String, String> keycloakIds = gebruikers.zorgVoorKlanten(klanten);
        gebruikers.zorgVoorBeheerder();

        database.schrijf(data, keycloakIds, properties.vanaf(), properties.tot());
        invarianten.controleer();
        log.info("Data gegenereerd en gecontroleerd");
    }
}
