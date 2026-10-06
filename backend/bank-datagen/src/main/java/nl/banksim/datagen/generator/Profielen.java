package nl.banksim.datagen.generator;

import static nl.banksim.datagen.generator.Profiel.Type.GEPENSIONEERD;
import static nl.banksim.datagen.generator.Profiel.Type.WERKNEMER;
import static nl.banksim.datagen.generator.Profiel.Type.ZZP;

import java.util.List;

import nl.banksim.domain.geld.Money;

/** De tien huishoudens uit het FO: alleenstaanden, stellen, gezinnen, gepensioneerden en een zzp'er. */
public final class Profielen {

    private Profielen() {
    }

    public static final List<Profiel> ALLE = List.of(
            new Profiel(1, "Jan", "de Vries", "jdevries", "Utrecht", WERKNEMER, Bedrijven.TECHNIEK_NOORD,
                    m("3100"), null, false, Bedrijven.WOONSTICHTING, m("950"), 1, 0, m("95"), null,
                    9, 2, 1, 2, m("400"), m("1800"), m("6500"), false, true, true),
            new Profiel(2, "Sanne", "Bakker", "sbakker", "Amersfoort", WERKNEMER, Bedrijven.ZORGGROEP,
                    m("4800"), null, true, Bedrijven.HYPOTHEEKBANK, m("1350"), 2, 0, m("140"), null,
                    10, 3, 1, 2, m("600"), m("2500"), m("14000"), true, true, true),
            new Profiel(3, "Mohamed", "El Amrani", "melamrani", "Utrecht", WERKNEMER, Bedrijven.LOGISTIEK,
                    m("4200"), null, false, Bedrijven.WONINGCORPORATIE, m("1250"), 5, 3, m("175"), null,
                    12, 3, 2, 1, m("250"), m("1500"), m("4500"), true, false, true),
            new Profiel(4, "Lisa", "Jansen", "ljansen", "Zuidstad", WERKNEMER, Bedrijven.ONDERWIJS,
                    m("2350"), null, false, Bedrijven.WOONSTICHTING, m("780"), 1, 0, m("75"), null,
                    8, 3, 1, 3, m("150"), m("900"), m("1800"), false, true, true),
            new Profiel(5, "Pieter", "Visser", "pvisser", "Amersfoort", GEPENSIONEERD, Bedrijven.PENSIOENFONDS,
                    m("900"), m("1550"), false, null, null, 2, 0, m("150"), null,
                    9, 1, 2, 1, m("200"), m("3000"), m("38000"), true, false, false),
            new Profiel(6, "Fatma", "Yilmaz", "fyilmaz", "Zuidstad", WERKNEMER, Bedrijven.GEMEENTE_ZUIDSTAD_WERK,
                    m("5200"), null, true, Bedrijven.HYPOTHEEKBANK, m("1450"), 4, 2, m("165"), m("450"),
                    11, 3, 1, 1, m("500"), m("2200"), m("16000"), true, true, true),
            new Profiel(7, "Daan", "Smit", "dsmit", "Utrecht", ZZP, null,
                    m("4650"), null, false, Bedrijven.HYPOTHEEKBANK, m("1100"), 1, 0, m("105"), null,
                    9, 4, 1, 2, m("500"), m("4000"), m("12000"), true, true, true),
            new Profiel(8, "Emma", "de Boer", "edeboer", "Amersfoort", WERKNEMER, Bedrijven.ADVIES,
                    m("3900"), null, true, Bedrijven.WONINGCORPORATIE, m("1150"), 2, 0, m("125"), null,
                    10, 3, 1, 3, m("450"), m("1700"), m("9000"), false, true, true),
            new Profiel(9, "Ruud", "Mulder", "rmulder", "Zuidstad", GEPENSIONEERD, Bedrijven.PENSIOENFONDS,
                    m("1100"), m("1500"), false, Bedrijven.WOONSTICHTING, m("850"), 1, 0, m("90"), null,
                    8, 1, 2, 1, m("150"), m("2000"), m("22000"), false, false, true),
            new Profiel(10, "Noor", "Hendriks", "nhendriks", "Utrecht", WERKNEMER, Bedrijven.BOUW,
                    m("6000"), null, true, Bedrijven.HYPOTHEEKBANK, m("1600"), 4, 2, m("185"), m("520"),
                    12, 4, 1, 2, m("800"), m("3500"), m("25000"), true, true, true));

    private static Money m(String bedrag) {
        return Money.of(bedrag);
    }
}
