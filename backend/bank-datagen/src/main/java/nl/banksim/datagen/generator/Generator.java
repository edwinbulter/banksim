package nl.banksim.datagen.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nl.banksim.datagen.model.Contact;
import nl.banksim.datagen.model.Dataset;
import nl.banksim.datagen.model.Rekening;
import nl.banksim.datagen.model.Rekeninghouder;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.rente.RenteCalculator;
import nl.banksim.domain.transactie.Overboeking;
import nl.banksim.domain.transactie.Partij;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Genereert ongeveer vijf jaar transacties voor tien huishoudens (TO §9). Deterministisch: dezelfde seed geeft
 * exact dezelfde data. Gebeurtenissen worden dag voor dag en chronologisch geboekt; betaal- en
 * spaarrekeningen komen nooit onder nul en spaarrekeningen krijgen maandelijks rente.
 */
public final class Generator {

    public static final ZoneId TIJDZONE = ZoneId.of("Europe/Amsterdam");
    private static final Comparator<Gepland> VOLGORDE =
            Comparator.comparing((Gepland g) -> g.gebeurtenis().tijd()).thenComparingInt(Gepland::huishouden);

    private final long seed;
    private final LocalDate vanaf;
    private final LocalDate totEnMet;
    private final RenteCalculator rente = RenteCalculator.standaard();

    private final Map<Iban, Money> saldo = new HashMap<>();
    private final Map<Iban, RekeningSoort> soort = new HashMap<>();
    private final Map<Iban, Map<LocalDate, Money>> spaarEindsaldi = new LinkedHashMap<>();
    private final List<Overboeking> overboekingen = new ArrayList<>();
    private int overgeslagen;

    public Generator(long seed, LocalDate vanaf, LocalDate totEnMet) {
        this.seed = seed;
        this.vanaf = vanaf;
        this.totEnMet = totEnMet;
    }

    public Dataset genereer() {
        List<Huishouden> huishoudens = Profielen.ALLE.stream().map(p -> new Huishouden(p, seed)).toList();
        for (Huishouden h : huishoudens) {
            h.kenVrienden(huishoudens.stream().filter(ander -> ander != h).map(Huishouden::betaal).toList());
        }

        List<Rekeninghouder> houders = new ArrayList<>();
        List<Rekening> rekeningen = new ArrayList<>();
        List<Contact> contacten = new ArrayList<>();
        for (Huishouden h : huishoudens) {
            Profiel p = h.profiel();
            UUID id = Ids.uuid("huishouden:" + p.gebruikersnaam());
            houders.add(new Rekeninghouder(id, p.naam(), Rekeninghouder.Soort.HUISHOUDEN, p.gebruikersnaam(),
                    p.voornaam(), p.achternaam()));
            rekeningen.add(open(h.betaal().iban(), id, RekeningSoort.BETAAL, p.startBetaal()));
            rekeningen.add(open(h.spaar().iban(), id, RekeningSoort.SPAAR, p.startSpaar()));
            contacten.add(new Contact(h.betaal().iban(), p.naam(), "particulier"));
            spaarEindsaldi.put(h.spaar().iban(), new HashMap<>());
        }
        for (Bedrijf b : Bedrijven.alle()) {
            Rekeninghouder.Soort houderSoort = Bedrijven.isIntern(b) ? Rekeninghouder.Soort.BANK : Rekeninghouder.Soort.BEDRIJF;
            houders.add(new Rekeninghouder(b.rekeninghouderId(), b.naam(), houderSoort, null, null, null));
            rekeningen.add(open(b.iban(), b.rekeninghouderId(), RekeningSoort.EXTERN, Money.ZERO));
            if (!Bedrijven.isIntern(b)) {
                contacten.add(new Contact(b.iban(), b.naam(), b.categorie()));
            }
        }

        for (LocalDate dag = vanaf; !dag.isAfter(totEnMet); dag = dag.plusDays(1)) {
            List<Gepland> vandaag = new ArrayList<>();
            for (int i = 0; i < huishoudens.size(); i++) {
                for (Gebeurtenis g : huishoudens.get(i).plan(dag)) {
                    vandaag.add(new Gepland(i, g, huishoudens.get(i)));
                }
            }
            vandaag.sort(VOLGORDE);
            for (Gepland g : vandaag) {
                verwerk(dag, g);
            }
            for (Map.Entry<Iban, Map<LocalDate, Money>> e : spaarEindsaldi.entrySet()) {
                e.getValue().put(dag, saldo.get(e.getKey()));
            }
            if (dag.equals(YearMonth.from(dag).atEndOfMonth())) {
                schrijfRenteBij(dag, huishoudens);
            }
        }
        return new Dataset(houders, rekeningen, contacten, overboekingen);
    }

    /** Aantal geplande bewegingen dat niet is geboekt omdat het saldo te laag was. */
    public int overgeslagen() {
        return overgeslagen;
    }

    private Rekening open(Iban iban, UUID houder, RekeningSoort rekeningSoort, Money opening) {
        saldo.put(iban, opening);
        soort.put(iban, rekeningSoort);
        return new Rekening(iban, houder, rekeningSoort, opening, vanaf);
    }

    private void verwerk(LocalDate dag, Gepland gepland) {
        Gebeurtenis g = gepland.gebeurtenis();
        Iban van = g.van().iban();
        if (g.afromenTot() != null) {
            Money overschot = saldo.get(van).minus(g.afromenTot());
            if (overschot.isGreaterThan(g.bedrag())) {
                g = new Gebeurtenis(g.tijd(), g.type(), g.van(), g.naar(), overschot, g.omschrijving(),
                        g.betalingskenmerk(), g.extraOmschrijving(), g.vast(), g.buffer(), null);
            }
        }
        if (soort.get(van).magNietRoodStaan()) {
            Money nodig = g.bedrag().plus(g.buffer());
            Money tekort = nodig.minus(saldo.get(van));
            if (tekort.isPositive()) {
                // Net als in het echt: bij een tekort eerst geld van de spaarrekening halen, behalve voor
                // optionele bewegingen met een buffer (sparen, vakantie boeken).
                boolean optioneel = g.buffer().isPositive();
                boolean vanBetaalrekening = van.equals(gepland.eigenaar().betaal().iban());
                if (optioneel || !vanBetaalrekening || !haalVanSpaarrekening(dag, gepland, tekort)) {
                    overgeslagen++;
                    return;
                }
            }
        }
        boek(dag, g.tijd(), g.type(), g.van(), g.naar(), g.bedrag(), g.omschrijving(), g.betalingskenmerk(),
                g.extraOmschrijving());
    }

    /** Vaste lasten gaan altijd door: bij een tekort eerst een ronde som van de spaarrekening halen. */
    private boolean haalVanSpaarrekening(LocalDate dag, Gepland gepland, Money tekort) {
        Huishouden h = gepland.eigenaar();
        long honderdtallen = tekort.amount().movePointLeft(2).setScale(0, RoundingMode.CEILING).longValueExact() + 1;
        Money opname = Money.of(BigDecimal.valueOf(honderdtallen * 100));
        if (saldo.get(h.spaar().iban()).isLessThan(opname)) {
            return false;
        }
        boek(dag, gepland.gebeurtenis().tijd().minusMinutes(1), TransactieType.OPNAME, h.spaar(), h.betaal(), opname,
                "Aanvulling betaalrekening", null, null);
        return true;
    }

    private void schrijfRenteBij(LocalDate laatsteDag, List<Huishouden> huishoudens) {
        YearMonth maand = YearMonth.from(laatsteDag);
        Partij bank = new Partij(Bedrijven.BANKSIM_RENTE.iban(), Bedrijven.BANKSIM_RENTE.naam());
        for (Huishouden h : huishoudens) {
            Map<LocalDate, Money> eindsaldi = spaarEindsaldi.get(h.spaar().iban());
            LocalDate eerste = maand.atDay(1).isBefore(vanaf) ? vanaf : maand.atDay(1);
            Money bedrag = Money.afgerond(rente.opgebouwd(eerste, laatsteDag, eindsaldi::get));
            if (bedrag.isPositive()) {
                boek(laatsteDag, LocalTime.of(23, 59), TransactieType.RENTE, bank, h.spaar(), bedrag,
                        "Rente " + Kalender.maand(laatsteDag), null, null);
            }
            eindsaldi.clear();
        }
    }

    private void boek(LocalDate dag, LocalTime tijd, TransactieType type, Partij van, Partij naar, Money bedrag,
                      String omschrijving, String kenmerk, String extra) {
        UUID id = Ids.uuid("overboeking:" + seed + ":" + overboekingen.size());
        Overboeking overboeking = new Overboeking(id, type, van, naar, bedrag, dag.atTime(tijd).atZone(TIJDZONE).toInstant(),
                dag, omschrijving, kenmerk, extra);
        overboekingen.add(overboeking);
        saldo.merge(van.iban(), bedrag.negate(), Money::plus);
        saldo.merge(naar.iban(), bedrag, Money::plus);
    }

    private record Gepland(int huishouden, Gebeurtenis gebeurtenis, Huishouden eigenaar) {
    }
}
