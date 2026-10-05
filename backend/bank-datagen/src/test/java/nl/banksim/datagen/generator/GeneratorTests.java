package nl.banksim.datagen.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nl.banksim.datagen.model.Contact;
import nl.banksim.datagen.model.Dataset;
import nl.banksim.datagen.model.Rekening;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.transactie.Boeking;
import nl.banksim.domain.transactie.Overboeking;
import nl.banksim.domain.transactie.TransactieType;

class GeneratorTests {

    static final LocalDate VANAF = LocalDate.of(2021, 10, 1);
    static final LocalDate TOT = LocalDate.of(2026, 12, 31);

    /**
     * Golden master (TO §13): verandert de generator, dan verandert deze waarde. Alleen bewust aanpassen,
     * na controle dat de nieuwe data klopt.
     */
    static final String GOLDEN_MASTER = "a8567f4a99c9680f";

    static Dataset data;

    @BeforeAll
    static void genereer() {
        data = new Generator(42, VANAF, TOT).genereer();
    }

    @Test
    void zelfdeSeedGeeftExactDezelfdeData() {
        Dataset nogmaals = new Generator(42, VANAF, TOT).genereer();
        assertThat(nogmaals.overboekingen()).isEqualTo(data.overboekingen());
        assertThat(nogmaals.rekeningen()).isEqualTo(data.rekeningen());
    }

    @Test
    void andereSeedGeeftAndereData() {
        Dataset ander = new Generator(7, VANAF, TOT).genereer();
        assertThat(ander.overboekingen()).isNotEqualTo(data.overboekingen());
    }

    @Test
    void goldenMaster() throws Exception {
        assertThat(checksum(data)).isEqualTo(GOLDEN_MASTER);
    }

    @Test
    void betaalEnSpaarrekeningenStaanNooitRood() {
        Map<Iban, RekeningSoort> soort = data.rekeningen().stream()
                .collect(Collectors.toMap(Rekening::iban, Rekening::soort));
        Map<Iban, Money> saldo = new HashMap<>();
        data.rekeningen().forEach(r -> saldo.put(r.iban(), r.openingssaldo()));
        Map<LocalDate, List<Boeking>> perDag = new TreeMap<>(data.overboekingen().stream()
                .flatMap(o -> o.boekingen().stream())
                .collect(Collectors.groupingBy(Boeking::boekdatum)));
        perDag.forEach((dag, boekingen) -> {
            boekingen.forEach(b -> saldo.merge(b.rekening(), b.bedrag(), Money::plus));
            saldo.forEach((iban, s) -> {
                if (soort.get(iban).magNietRoodStaan()) {
                    assertThat(s.isNegative()).as("saldo %s op %s", iban, dag).isFalse();
                }
            });
        });
    }

    @Test
    void elkeBoekingHoortBijEenBestaandeRekening() {
        Set<Iban> rekeningen = data.rekeningen().stream().map(Rekening::iban).collect(Collectors.toSet());
        assertThat(data.overboekingen()).allSatisfy(o -> {
            assertThat(rekeningen).contains(o.van().iban(), o.naar().iban());
            assertThat(o.uitvoerDatum()).isBetween(VANAF, TOT);
        });
    }

    @Test
    void elkHuishoudenHeeftElkeMaandInkomenEnRente() {
        Map<YearMonth, List<Overboeking>> perMaand = data.overboekingen().stream()
                .collect(Collectors.groupingBy(o -> YearMonth.from(o.uitvoerDatum())));
        for (Profiel p : Profielen.ALLE) {
            Iban betaal = Huishouden.betaalIban(p);
            Iban spaar = Huishouden.spaarIban(p);
            for (YearMonth maand = YearMonth.from(VANAF); !maand.isAfter(YearMonth.from(TOT)); maand = maand.plusMonths(1)) {
                YearMonth m = maand;
                List<Overboeking> inMaand = perMaand.get(m);
                assertThat(inMaand).as("inkomen %s %s", p.naam(), m).anySatisfy(o -> {
                    assertThat(o.naar().iban()).isEqualTo(betaal);
                    assertThat(o.type()).isIn(TransactieType.VERZAMELBETALING, TransactieType.OVERSCHRIJVING);
                });
                assertThat(inMaand).as("rente %s %s", p.naam(), m).anySatisfy(o -> {
                    assertThat(o.naar().iban()).isEqualTo(spaar);
                    assertThat(o.type()).isEqualTo(TransactieType.RENTE);
                    assertThat(o.uitvoerDatum()).isEqualTo(m.atEndOfMonth());
                });
            }
        }
    }

    @Test
    void elkHuishoudenSpaartGeregeld() {
        for (Profiel p : Profielen.ALLE) {
            long inleggen = data.overboekingen().stream()
                    .filter(o -> o.type() == TransactieType.INLEG && o.naar().iban().equals(Huishouden.spaarIban(p)))
                    .count();
            assertThat(inleggen).as(p.naam()).isGreaterThanOrEqualTo(30);
        }
    }

    @Test
    void alleTransactietypenUitHetFoKomenVoor() {
        Set<TransactieType> typen = data.overboekingen().stream().map(Overboeking::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TransactieType.class)));
        assertThat(typen).containsAll(EnumSet.allOf(TransactieType.class));
    }

    @Test
    void geloofwaardigVolume() {
        assertThat(data.overboekingen()).hasSizeBetween(20_000, 45_000);
        assertThat(data.rekeningen().stream().filter(r -> r.soort() != RekeningSoort.EXTERN)).hasSize(20);
    }

    @Test
    void contactenZijnBetaalrekeningenEnBedrijvenZonderInterneRekeningen() {
        Set<Iban> contacten = data.contacten().stream().map(Contact::iban).collect(Collectors.toSet());
        for (Profiel p : Profielen.ALLE) {
            assertThat(contacten).contains(Huishouden.betaalIban(p)).doesNotContain(Huishouden.spaarIban(p));
        }
        assertThat(contacten).doesNotContain(Bedrijven.BANKSIM_RENTE.iban(), Bedrijven.BANKSIM_KAS.iban())
                .contains(Bedrijven.ENERGIE.iban(), Bedrijven.WOONSTICHTING.iban());
        assertThat(data.contacten()).allSatisfy(c -> assertThat(c.naam()).hasSizeBetween(1, 70));
    }

    @Test
    void ibansZijnUniek() {
        assertThat(data.rekeningen().stream().map(Rekening::iban).distinct()).hasSize(data.rekeningen().size());
    }

    static String checksum(Dataset dataset) throws Exception {
        Map<String, Money> eindsaldo = new TreeMap<>();
        dataset.rekeningen().forEach(r -> eindsaldo.put(r.iban().value(), r.openingssaldo()));
        dataset.overboekingen().forEach(o -> o.boekingen()
                .forEach(b -> eindsaldo.merge(b.rekening().value(), b.bedrag(), Money::plus)));
        StringBuilder sb = new StringBuilder("overboekingen=" + dataset.overboekingen().size() + "\n");
        eindsaldo.forEach((iban, saldo) -> sb.append(iban).append('=').append(saldo).append('\n'));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash).substring(0, 16);
    }
}
