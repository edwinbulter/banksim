package nl.banksim.datagen.generator;

import static nl.banksim.datagen.generator.Kalender.isWerkdag;
import static nl.banksim.datagen.generator.Kalender.maand;
import static nl.banksim.datagen.generator.Kalender.werkdagOpOfNa;
import static nl.banksim.datagen.generator.Kalender.werkdagOpOfVoor;
import static nl.banksim.domain.transactie.TransactieType.BETAALAUTOMAAT;
import static nl.banksim.domain.transactie.TransactieType.GELDAUTOMAAT;
import static nl.banksim.domain.transactie.TransactieType.IDEAL_WERO;
import static nl.banksim.domain.transactie.TransactieType.INCASSO;
import static nl.banksim.domain.transactie.TransactieType.INLEG;
import static nl.banksim.domain.transactie.TransactieType.ONLINE_BANKIEREN;
import static nl.banksim.domain.transactie.TransactieType.OPNAME;
import static nl.banksim.domain.transactie.TransactieType.OVERSCHRIJVING;
import static nl.banksim.domain.transactie.TransactieType.VERZAMELBETALING;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.Partij;

/** Plant per dag de geldbewegingen van één huishouden volgens zijn {@link Profiel} (FO §Fake data). */
final class Huishouden {

    private static final List<String> TIKKIE_REDENEN =
            List.of("Etentje", "Cadeau verjaardag", "Bioscoop", "Boodschappen gedeeld", "Weekendje weg", "Concertkaartjes");

    private final Profiel profiel;
    private final Partij betaal;
    private final Partij spaar;
    private final Toeval toeval;
    private final Map<Integer, Jaarplan> jaarplannen = new HashMap<>();
    private List<Partij> vrienden = List.of();
    private Money zzpOmzetKwartaal = Money.ZERO;
    private YearMonth laatsteFactuurMaand;

    Huishouden(Profiel profiel, long seed) {
        this.profiel = profiel;
        this.betaal = new Partij(betaalIban(profiel), profiel.naam());
        this.spaar = new Partij(spaarIban(profiel), profiel.naam());
        this.toeval = new Toeval(seed * 31 + profiel.nummer());
    }

    static Iban betaalIban(Profiel profiel) {
        return Iban.nl(Bedrijven.BANKCODE, 100_000L + profiel.nummer() * 10L + 1);
    }

    static Iban spaarIban(Profiel profiel) {
        return Iban.nl(Bedrijven.BANKCODE, 100_000L + profiel.nummer() * 10L + 2);
    }

    Profiel profiel() {
        return profiel;
    }

    Partij betaal() {
        return betaal;
    }

    Partij spaar() {
        return spaar;
    }

    void kenVrienden(List<Partij> anderen) {
        this.vrienden = List.copyOf(anderen);
    }

    /** Alle geplande bewegingen van vandaag; de generator boekt ze en bewaakt het saldo. */
    List<Gebeurtenis> plan(LocalDate dag) {
        List<Gebeurtenis> lijst = new ArrayList<>();
        Jaarplan jaar = jaarplannen.computeIfAbsent(dag.getYear(), j -> new Jaarplan(j, toeval));
        inkomen(dag, lijst);
        vasteLasten(dag, lijst);
        dagelijks(dag, lijst);
        jaarlijks(dag, jaar, lijst);
        sparen(dag, jaar, lijst);
        return lijst;
    }

    private void inkomen(LocalDate dag, List<Gebeurtenis> lijst) {
        BigDecimal loon = Kalender.loonstijging(dag.getYear());
        switch (profiel.type()) {
            case WERKNEMER -> {
                if (dag.equals(werkdagOpOfVoor(dag, 24))) {
                    Money salaris = maal(profiel.netto(), loon);
                    lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(profiel.inkomenBron()), betaal,
                            salaris, "Salaris " + maand(dag)).metKenmerk("Loonstrook " + dag.getYear() + "-" + dag.getMonthValue()));
                    if (dag.getMonth() == Month.MAY) {
                        lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(profiel.inkomenBron()), betaal,
                                maal(salaris, new BigDecimal("0.92")), "Vakantietoeslag " + dag.getYear()));
                    }
                    if (dag.getMonth() == Month.DECEMBER && profiel.dertiendeMaand()) {
                        lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(profiel.inkomenBron()), betaal,
                                maal(salaris, new BigDecimal("0.90")), "Eindejaarsuitkering " + dag.getYear()));
                    }
                }
            }
            case GEPENSIONEERD -> {
                if (dag.equals(werkdagOpOfVoor(dag, 23))) {
                    Money aow = maal(profiel.aow(), loon);
                    lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(Bedrijven.RIJKSUITKERINGEN), betaal,
                            aow, "AOW " + maand(dag)));
                    if (dag.getMonth() == Month.MAY) {
                        lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(Bedrijven.RIJKSUITKERINGEN), betaal,
                                maal(aow, new BigDecimal("0.50")), "Vakantiegeld AOW " + dag.getYear()));
                    }
                }
                if (dag.equals(werkdagOpOfVoor(dag, 25))) {
                    lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(profiel.inkomenBron()), betaal,
                            maal(profiel.netto(), loon), "Pensioen " + maand(dag)));
                }
            }
            case ZZP -> {
                // Minstens één betaalde factuur per maand: uiterlijk op de laatste werkdag vóór de 20e.
                boolean nogGeenFactuur = !YearMonth.from(dag).equals(laatsteFactuurMaand);
                boolean uiterlijk = nogGeenFactuur && dag.equals(werkdagOpOfVoor(dag, 20));
                if (isWerkdag(dag) && (uiterlijk || toeval.perMaand(3, 140))) {
                    laatsteFactuurMaand = YearMonth.from(dag);
                    Bedrijf klant = toeval.kies(List.of(Bedrijven.KLANT_ALFA, Bedrijven.KLANT_BETA, Bedrijven.KLANT_GAMMA));
                    Money factuur = maal(toeval.bedrag("1000.00", "2400.00"), loon);
                    zzpOmzetKwartaal = zzpOmzetKwartaal.plus(factuur);
                    lijst.add(Gebeurtenis.van(toeval.tijd(9, 17), OVERSCHRIJVING, partij(klant), betaal, factuur,
                            "Factuur " + dag.getYear() + "-" + toeval.cijfers(3)));
                }
                if (dag.getMonthValue() % 3 == 1 && dag.equals(werkdagOpOfVoor(dag, 28)) && zzpOmzetKwartaal.isPositive()) {
                    Money btw = maal(zzpOmzetKwartaal, new BigDecimal("0.1736"));
                    zzpOmzetKwartaal = Money.ZERO;
                    lijst.add(Gebeurtenis.van(toeval.tijd(9, 17), ONLINE_BANKIEREN, betaal, partij(Bedrijven.BELASTINGDIENST), btw,
                            "Omzetbelasting kwartaal").metKenmerk(toeval.cijfers(16)).alsVasteLast());
                }
                if (dag.equals(werkdagOpOfNa(dag, 28))) {
                    lijst.add(Gebeurtenis.van(toeval.tijd(4, 8), INCASSO, betaal, partij(Bedrijven.BELASTINGDIENST),
                            maal(Money.of("650.00"), loon), "Voorlopige aanslag inkomstenbelasting " + dag.getYear())
                            .metKenmerk(toeval.cijfers(16)).alsVasteLast());
                }
            }
        }
        if (profiel.kinderen() > 0 && dag.getMonthValue() % 3 == 1 && dag.equals(werkdagOpOfNa(dag, 1))) {
            Money kinderbijslag = maal(Money.of("290.00"), Kalender.inflatie(dag.getYear()), profiel.kinderen());
            lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), VERZAMELBETALING, partij(Bedrijven.RIJKSUITKERINGEN), betaal,
                    kinderbijslag, "Kinderbijslag kwartaal " + ((dag.getMonthValue() + 2) / 3) + " " + dag.getYear()));
        }
    }

    private void vasteLasten(LocalDate dag, List<Gebeurtenis> lijst) {
        int jaar = dag.getYear();
        BigDecimal inflatie = Kalender.inflatie(jaar);
        if (dag.equals(werkdagOpOfNa(dag, 1))) {
            if (profiel.woonlastenPartij() != null) {
                boolean huur = profiel.woonlastenPartij() != Bedrijven.HYPOTHEEKBANK;
                Money bedrag = huur ? maal(profiel.woonlasten(), inflatie) : profiel.woonlasten();
                incasso(lijst, profiel.woonlastenPartij(), bedrag, (huur ? "Huur " : "Hypotheek ") + maand(dag));
            }
            incasso(lijst, Bedrijven.ZORGVERZEKERAAR, maal(Money.of("140.00"), inflatie, profiel.volwassenen()),
                    "Premie zorgverzekering " + maand(dag));
            Money verzekering = profiel.auto() ? Money.of("73.00") : Money.of("18.00");
            incasso(lijst, Bedrijven.VERZEKERAAR, maal(verzekering, inflatie),
                    profiel.auto() ? "Pakketpolis wonen en auto" : "Inboedel- en aansprakelijkheidsverzekering");
        }
        if (profiel.kinderopvang() != null && dag.equals(werkdagOpOfNa(dag, 5))) {
            incasso(lijst, Bedrijven.KINDEROPVANG, maal(profiel.kinderopvang(), inflatie), "Kinderopvang " + maand(dag));
        }
        if (profiel.streaming() && dag.equals(werkdagOpOfNa(dag, 12))) {
            incasso(lijst, Bedrijven.STREAMING, maal(Money.of("13.99"), inflatie), "Abonnement " + maand(dag));
        }
        if (dag.equals(werkdagOpOfNa(dag, 15))) {
            incasso(lijst, Bedrijven.ENERGIE, maal(profiel.energie(), Kalender.energie(jaar)), "Termijnbedrag energie " + maand(dag));
        }
        if (dag.equals(werkdagOpOfNa(dag, 20))) {
            incasso(lijst, Bedrijven.INTERNET, maal(Money.of("45.00"), inflatie), "Internet en tv " + maand(dag));
            if (dag.getMonthValue() % 3 == 2) {
                incasso(lijst, Bedrijven.WATER, maal(Money.of("22.00"), inflatie, profiel.personen()), "Water kwartaalnota");
            }
        }
        if (dag.equals(werkdagOpOfNa(dag, 22))) {
            incasso(lijst, Bedrijven.MOBIEL, maal(Money.of("17.50"), inflatie, profiel.volwassenen()), "Mobiel abonnement " + maand(dag));
        }
        if (profiel.sport() && dag.equals(werkdagOpOfNa(dag, 26))) {
            incasso(lijst, Bedrijven.SPORTSCHOOL, maal(Money.of("34.95"), inflatie), "Lidmaatschap " + maand(dag));
        }
        if (dag.getMonthValue() >= 3 && dag.equals(werkdagOpOfNa(dag, 28))) {
            Money termijn = profiel.personen() == 1 ? Money.of("38.00") : Money.of("55.00");
            incasso(lijst, Bedrijven.GEMEENTE_ZUIDSTAD, maal(termijn, inflatie),
                    "Gemeentelijke belastingen termijn " + (dag.getMonthValue() - 2) + "/10");
        }
    }

    private void dagelijks(LocalDate dag, List<Gebeurtenis> lijst) {
        boolean zaterdag = dag.getDayOfWeek() == DayOfWeek.SATURDAY;
        boolean december = dag.getMonth() == Month.DECEMBER;
        BigDecimal inflatie = Kalender.inflatie(dag.getYear());

        if (dag.getDayOfWeek() != DayOfWeek.SUNDAY && toeval.perMaand(profiel.boodschappen(), zaterdag ? 180 : 95)) {
            Bedrijf winkel = toeval.kies(List.of(Bedrijven.BUURTSUPER, Bedrijven.PRIJSVAST, Bedrijven.VERSMARKT));
            Money bedrag = zaterdag ? toeval.bedrag("35.00", "150.00") : toeval.bedrag("6.00", "60.00");
            if (december) {
                bedrag = maal(bedrag, new BigDecimal("1.25"));
            }
            lijst.add(winkel(dag, winkel, maal(bedrag, inflatie)));
        }
        if (toeval.perMaand(2, 100)) {
            lijst.add(winkel(dag, Bedrijven.BAKKERIJ, maal(toeval.bedrag("3.00", "15.00"), inflatie)));
        }
        if (toeval.perMaand(1, 100)) {
            lijst.add(winkel(dag, Bedrijven.DROGISTERIJ, maal(toeval.bedrag("4.00", "40.00"), inflatie)));
        }
        if (profiel.auto() && toeval.perMaand(3, 100)) {
            lijst.add(winkel(dag, Bedrijven.TANKSTATION, maal(toeval.bedrag("45.00", "85.00"), Kalender.energie(dag.getYear()).min(new BigDecimal("1.30")))));
        }
        if (toeval.perMaand(profiel.web(), december ? 200 : 100)) {
            Bedrijf winkel = toeval.kies(List.of(Bedrijven.WEBWINKEL, Bedrijven.BOEKENBOX, Bedrijven.MODE, Bedrijven.ELEKTRO));
            Money bedrag = winkel == Bedrijven.ELEKTRO ? toeval.bedrag("25.00", "250.00") : toeval.bedrag("10.00", "120.00");
            lijst.add(Gebeurtenis.van(toeval.tijd(7, 23), IDEAL_WERO, betaal, partij(winkel), maal(bedrag, inflatie),
                    "Bestelling " + toeval.cijfers(8)).metKenmerk(toeval.cijfers(16)));
        }
        if (toeval.perMaand(profiel.personen() > 2 ? 4 : 3, december ? 150 : 100)) {
            Bedrijf zaak = toeval.kies(List.of(Bedrijven.EETCAFE, Bedrijven.RESTAURANT));
            lijst.add(winkel(dag, zaak, maal(toeval.bedrag("12.00", "45.00"), inflatie, Math.min(profiel.personen(), 3))));
        }
        // Overige bestedingen (kleding, huis, hobby): samen ongeveer een kwart van het netto inkomen.
        if (toeval.perMaand(8, december ? 160 : 100)) {
            Bedrijf zaak = profiel.kinderen() > 0
                    ? toeval.kies(List.of(Bedrijven.MODEHUIS, Bedrijven.BOUWMARKT, Bedrijven.WOONWARENHUIS, Bedrijven.SPEELGOED))
                    : toeval.kies(List.of(Bedrijven.MODEHUIS, Bedrijven.BOUWMARKT, Bedrijven.WOONWARENHUIS));
            Money gemiddeld = maal(inkomen(), new BigDecimal("0.03"));
            Money bedrag = maal(gemiddeld, new BigDecimal(toeval.tussen(30, 170)).movePointLeft(2));
            lijst.add(winkel(dag, zaak, bedrag.isLessThan(Money.of("5.00")) ? Money.of("5.00") : bedrag));
        }
        if (profiel.auto() && toeval.perMaand(1, 20)) {
            lijst.add(winkel(dag, Bedrijven.GARAGE, maal(toeval.bedrag("120.00", "950.00"), inflatie)));
        }
        if (toeval.perMaand(profiel.pinnen(), 100)) {
            lijst.add(Gebeurtenis.van(toeval.tijd(9, 22), GELDAUTOMAAT, betaal,
                    new Partij(Bedrijven.BANKSIM_KAS.iban(), "Geldautomaat " + profiel.stad() + " Centrum"),
                    toeval.ronde(20, 200, 10), "Geldopname " + profiel.stad()));
        }
        if (!vrienden.isEmpty() && toeval.perMaand(profiel.tikkies(), 100)) {
            lijst.add(Gebeurtenis.van(toeval.tijd(10, 23), ONLINE_BANKIEREN, betaal, toeval.kies(vrienden),
                    toeval.bedrag("5.00", "80.00"), toeval.kies(TIKKIE_REDENEN)));
        }
    }

    private void jaarlijks(LocalDate dag, Jaarplan jaar, List<Gebeurtenis> lijst) {
        if (!dag.isBefore(jaar.vakantieStart) && dag.isBefore(jaar.vakantieStart.plusDays(14)) && toeval.procent(70)) {
            lijst.add(winkel(dag, Bedrijven.VAKANTIEPARK, maal(toeval.bedrag("15.00", "70.00"),
                    Kalender.inflatie(dag.getYear()), Math.min(profiel.personen(), 3))));
        }
        if (dag.equals(jaar.vakantieBoeking)) {
            Money reis = maal(toeval.bedrag("450.00", "700.00"), Kalender.inflatie(dag.getYear()), profiel.personen());
            lijst.add(Gebeurtenis.van(toeval.tijd(19, 23), IDEAL_WERO, betaal, partij(Bedrijven.REISBUREAU), reis,
                    "Boeking zomervakantie " + dag.getYear()).metKenmerk(toeval.cijfers(16)).metBuffer(Money.of("300.00")));
        }
        if (dag.equals(jaar.belastingTeruggave) && profiel.type() != Profiel.Type.ZZP) {
            lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), OVERSCHRIJVING, partij(Bedrijven.BELASTINGDIENST), betaal,
                    toeval.bedrag("80.00", "1200.00"), "Teruggaaf inkomstenbelasting " + (dag.getYear() - 1))
                    .metKenmerk(toeval.cijfers(16)));
        }
        if (dag.getMonth() == Month.MARCH && dag.equals(werkdagOpOfNa(dag, 10))) {
            Money verschil = toeval.bedrag("0.00", "400.00").minus(Money.of("150.00"));
            if (verschil.isPositive()) {
                incasso(lijst, Bedrijven.ENERGIE, verschil, "Jaarafrekening energie " + (dag.getYear() - 1));
            } else if (verschil.isNegative()) {
                lijst.add(Gebeurtenis.van(toeval.tijd(1, 6), OVERSCHRIJVING, partij(Bedrijven.ENERGIE), betaal,
                        verschil.abs(), "Teruggave jaarafrekening energie " + (dag.getYear() - 1)));
            }
        }
    }

    private void sparen(LocalDate dag, Jaarplan jaar, List<Gebeurtenis> lijst) {
        if (dag.equals(werkdagOpOfNa(dag, 25))) {
            Money inleg = maal(profiel.sparen(), Kalender.loonstijging(dag.getYear()));
            // Automatisch sparen: vast bedrag, en daarbij alles boven een buffer van ongeveer een maand netto inkomen.
            Money doelsaldo = maal(inkomen(), new BigDecimal("1.30"));
            lijst.add(Gebeurtenis.van(toeval.tijd(9, 11), INLEG, betaal, spaar, inleg, "Maandelijks sparen")
                    .metBuffer(Money.of("600.00")).afromenTot(doelsaldo));
        }
        if (dag.getMonth() == Month.MAY && dag.equals(werkdagOpOfNa(dag, 27))) {
            lijst.add(Gebeurtenis.van(toeval.tijd(9, 11), INLEG, betaal, spaar,
                    maal(profiel.netto(), new BigDecimal("0.40")), "Extra inleg vakantiegeld").metBuffer(Money.of("800.00")));
        }
        if (jaar.opnames.contains(dag)) {
            lijst.add(Gebeurtenis.van(toeval.tijd(9, 22), OPNAME, spaar, betaal, toeval.ronde(200, 1500, 50),
                    toeval.kies(List.of("Nieuwe wasmachine", "Tandarts", "Reparatie auto", "Verjaardag", "Meubels", "Opname"))));
        }
    }

    private void incasso(List<Gebeurtenis> lijst, Bedrijf partij, Money bedrag, String omschrijving) {
        lijst.add(Gebeurtenis.van(toeval.tijd(4, 8), INCASSO, betaal, partij(partij), bedrag, omschrijving)
                .metKenmerk(toeval.cijfers(16)).alsVasteLast());
    }

    private Gebeurtenis winkel(LocalDate dag, Bedrijf winkel, Money bedrag) {
        String filiaal = winkel.naam() + " " + toeval.cijfers(4) + " " + profiel.stad();
        return Gebeurtenis.van(toeval.tijd(8, 21), BETAALAUTOMAAT, betaal, new Partij(winkel.iban(), filiaal), bedrag,
                "Betaalpas " + dag.getDayOfMonth() + "-" + dag.getMonthValue());
    }

    /** Netto maandinkomen inclusief AOW, in prijzen van 2021. */
    private Money inkomen() {
        return profiel.aow() == null ? profiel.netto() : profiel.netto().plus(profiel.aow());
    }

    private static Partij partij(Bedrijf bedrijf) {
        return new Partij(bedrijf.iban(), bedrijf.naam());
    }

    static Money maal(Money bedrag, BigDecimal factor) {
        return Money.afgerond(bedrag.amount().multiply(factor));
    }

    static Money maal(Money bedrag, BigDecimal factor, int aantal) {
        return Money.afgerond(bedrag.amount().multiply(factor).multiply(BigDecimal.valueOf(aantal)));
    }

    /** Dingen die één keer per jaar gebeuren, vooraf ingepland. */
    private static final class Jaarplan {

        final LocalDate vakantieBoeking;
        final LocalDate vakantieStart;
        final LocalDate belastingTeruggave;
        final List<LocalDate> opnames = new ArrayList<>();

        Jaarplan(int jaar, Toeval toeval) {
            vakantieBoeking = LocalDate.of(jaar, toeval.tussen(1, 3), toeval.tussen(1, 28));
            vakantieStart = LocalDate.of(jaar, toeval.tussen(7, 8), toeval.tussen(1, 17));
            belastingTeruggave = werkdagOpOfNa(LocalDate.of(jaar, toeval.tussen(4, 6), 1), toeval.tussen(1, 28));
            int aantal = toeval.tussen(1, 3);
            for (int i = 0; i < aantal; i++) {
                opnames.add(LocalDate.of(jaar, toeval.tussen(1, 12), toeval.tussen(1, 28)));
            }
        }
    }
}
