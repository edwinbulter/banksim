package nl.banksim.api.integratie;

import static nl.banksim.api.integratie.Fixture.ENECO_IBAN;
import static nl.banksim.api.integratie.Fixture.JAN_BETAAL;
import static nl.banksim.api.integratie.Fixture.JAN_SPAAR;
import static nl.banksim.api.integratie.Fixture.PIET_BETAAL;
import static nl.banksim.api.integratie.Fixture.PIET_SPAAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rente.RenteCalculator;

class BetalingApiTests extends IntegratieTest {

    @BeforeEach
    void data() {
        laadFixture();
    }

    @Test
    void betalingNaarAnderHuishoudenWordtAfEnBijgeschreven() throws Exception {
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "10.00", "Piet Pieters", PIET_BETAAL, "Etentje")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bedrag").value("10.00"))
                .andExpect(jsonPath("$.datum").value("2026-10-05"))
                .andExpect(jsonPath("$.saldo").value("2905.00"));

        mvc.perform(get("/api/accounts/{iban}", PIET_BETAAL).with(piet())).andExpect(jsonPath("$.saldo").value("510.00"));
        mvc.perform(get("/api/accounts/{iban}/transactions", PIET_BETAAL).with(piet()))
                .andExpect(jsonPath("$.items[0].bedrag").value("10.00"))
                .andExpect(jsonPath("$.items[0].tegenNaam").value("Jan de Vries"))
                .andExpect(jsonPath("$.items[0].typeLabel").value("Online bankieren"))
                .andExpect(jsonPath("$.items[0].omschrijving").value("Etentje"));
        assertThat(beheer.queryForObject("SELECT count(*) FROM audit_log WHERE actie = 'BETALING' AND actor = 'jdevries'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void naamMagAndereHoofdlettersEnSpatiesHebben() throws Exception {
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "  piet   PIETERS ", PIET_BETAAL, null)
                .andExpect(status().isCreated());
    }

    @Test
    void nooitRoodOokNietDoorEenLatereAfschrijving() throws Exception {
        // Saldo op D is 2.915, maar op 20 oktober gaat er 900 af: meer dan 2.015 betalen kan dus niet.
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "2500.00", "Eneco", ENECO_IBAN, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/saldo-ontoereikend"));
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "2015.00", "Eneco", ENECO_IBAN, null)
                .andExpect(status().isCreated());
        assertThat(beheer.queryForObject("SELECT count(*) FROM overboeking WHERE type = 'ONLINE_BANKIEREN'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void alleenNaarBekendeContactenMetDeJuisteNaam() throws Exception {
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "Piet Pieters", PIET_SPAAR, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/onbekende-ontvanger"));
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "Kees", PIET_BETAAL, null)
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/naam-past-niet"));
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "Piet Pieters", "NL00SIMB0000100021", null)
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/ongeldig-iban"));
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "Jan de Vries", JAN_BETAAL, null)
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/zelfde-rekening"));
    }

    @Test
    void alleenVanafDeEigenBetaalrekening() throws Exception {
        betaal(jan(), UUID.randomUUID(), PIET_BETAAL, "5.00", "Eneco", ENECO_IBAN, null).andExpect(status().isNotFound());
        betaal(jan(), UUID.randomUUID(), JAN_SPAAR, "5.00", "Eneco", ENECO_IBAN, null)
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/alleen-van-betaalrekening"));
        betaal(beheerder(), UUID.randomUUID(), JAN_BETAAL, "5.00", "Eneco", ENECO_IBAN, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void ongeldigeInvoerGeeft400() throws Exception {
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "0.00", "Eneco", ENECO_IBAN, null).andExpect(status().isBadRequest());
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "1.001", "Eneco", ENECO_IBAN, null).andExpect(status().isBadRequest());
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "-5.00", "Eneco", ENECO_IBAN, null).andExpect(status().isBadRequest());
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "Eneco", ENECO_IBAN, "x".repeat(141))
                .andExpect(status().isBadRequest());
        betaal(jan(), UUID.randomUUID(), JAN_BETAAL, "5.00", "x".repeat(71), ENECO_IBAN, null).andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments").with(jan()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(JAN_BETAAL, "5.00", "Eneco", ENECO_IBAN, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zelfdeSleutelBoektMaarEenKeer() throws Exception {
        UUID sleutel = UUID.randomUUID();
        String eerste = betaal(jan(), sleutel, JAN_BETAAL, "7.00", "Eneco", ENECO_IBAN, "Herhaald")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String tweede = betaal(jan(), sleutel, JAN_BETAAL, "7.00", "Eneco", ENECO_IBAN, "Herhaald")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(tweede, "$.overboekingId")).isEqualTo(JsonPath.read(eerste, "$.overboekingId"));
        assertThat(beheer.queryForObject("SELECT count(*) FROM overboeking WHERE omschrijving = 'Herhaald'", Integer.class))
                .isEqualTo(1);
        betaal(jan(), sleutel, JAN_BETAAL, "8.00", "Eneco", ENECO_IBAN, "Herhaald")
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/idempotency-key-hergebruikt"));
    }

    @Test
    void mislukteBetalingMagMetDezelfdeSleutelOpnieuw() throws Exception {
        UUID sleutel = UUID.randomUUID();
        betaal(jan(), sleutel, JAN_BETAAL, "2500.00", "Eneco", ENECO_IBAN, null).andExpect(status().isUnprocessableContent());
        betaal(jan(), sleutel, JAN_BETAAL, "25.00", "Eneco", ENECO_IBAN, null).andExpect(status().isCreated());
    }

    @Test
    void inlegOpSpaarrekeningCorrigeertDeRenteVanLatereMaanden() throws Exception {
        overschrijf(jan(), JAN_BETAAL, JAN_SPAAR, "1000.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value("1915.00"));
        mvc.perform(get("/api/accounts/{iban}", JAN_SPAAR).with(jan())).andExpect(jsonPath("$.saldo").value("6010.00"));
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("size", "1").with(jan()))
                .andExpect(jsonPath("$.items[0].typeLabel").value("Overschrijving"))
                .andExpect(jsonPath("$.items[0].tegenNaam").value("Spaarrekening Jan de Vries"))
                .andExpect(jsonPath("$.items[0].omschrijving").value("Inleg"));
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_SPAAR).param("size", "1").with(jan()))
                .andExpect(jsonPath("$.items[0].typeLabel").value("Inleg"))
                .andExpect(jsonPath("$.items[0].tegenNaam").value("Betaalrekening Jan de Vries"));

        // Na de correcties is de rente van oktober t/m december precies wat de calculator over het saldo zegt.
        RenteCalculator calculator = RenteCalculator.standaard();
        for (LocalDate einde : new LocalDate[] {LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31)}) {
            Map<LocalDate, Money> saldi = eindsaldiZonderRenteVan(JAN_SPAAR, einde);
            Money verwacht = calculator.renteVoorMaand(java.time.YearMonth.from(einde), saldi::get);
            BigDecimal geboekt = beheer.queryForObject("""
                    SELECT sum(b.bedrag) FROM boeking b JOIN overboeking o ON o.id = b.overboeking_id
                    WHERE b.rekening_iban = ? AND o.type = 'RENTE' AND b.boekdatum = ?
                    """, BigDecimal.class, JAN_SPAAR, einde);
            assertThat(Money.of(geboekt)).as("rente %s", einde).isEqualTo(verwacht);
        }
        assertThat(beheer.queryForObject("SELECT count(*) FROM overboeking WHERE omschrijving LIKE 'Rentecorrectie%'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void opnameNooitMeerDanHetSpaarsaldo() throws Exception {
        overschrijf(jan(), JAN_SPAAR, JAN_BETAAL, "6000.00").andExpect(status().isUnprocessableContent());
        overschrijf(jan(), JAN_SPAAR, JAN_BETAAL, "1000.00").andExpect(status().isCreated());
        overschrijf(jan(), JAN_BETAAL, PIET_SPAAR, "10.00").andExpect(status().isNotFound());
        overschrijf(beheerder(), JAN_BETAAL, JAN_SPAAR, "10.00").andExpect(status().isForbidden());
    }

    private Map<LocalDate, Money> eindsaldiZonderRenteVan(String iban, LocalDate einde) {
        LocalDate van = einde.withDayOfMonth(1);
        Money saldo = Money.of(beheer.queryForObject("""
                SELECT r.openingssaldo + COALESCE((SELECT sum(bedrag) FROM boeking WHERE rekening_iban = r.iban AND boekdatum < ?), 0)
                FROM rekening r WHERE iban = ?
                """, BigDecimal.class, van, iban));
        Map<LocalDate, Money> saldi = new HashMap<>();
        for (LocalDate dag = van; !dag.isAfter(einde); dag = dag.plusDays(1)) {
            BigDecimal mutatie = beheer.queryForObject("""
                    SELECT COALESCE(sum(b.bedrag), 0) FROM boeking b JOIN overboeking o ON o.id = b.overboeking_id
                    WHERE b.rekening_iban = ? AND b.boekdatum = ? AND NOT (o.type = 'RENTE' AND b.boekdatum = ?)
                    """, BigDecimal.class, iban, dag, einde);
            saldo = saldo.plus(Money.of(mutatie));
            saldi.put(dag, saldo);
        }
        return saldi;
    }

    private ResultActions betaal(JwtRequestPostProcessor wie, UUID sleutel, String van, String bedrag, String naam,
                                 String iban, String omschrijving) throws Exception {
        return mvc.perform(post("/api/payments").with(wie).header("Idempotency-Key", sleutel.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json(van, bedrag, naam, iban, omschrijving)));
    }

    private ResultActions overschrijf(JwtRequestPostProcessor wie, String van, String naar, String bedrag) throws Exception {
        return mvc.perform(post("/api/transfers").with(wie).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vanIban\":\"%s\",\"naarIban\":\"%s\",\"bedrag\":\"%s\"}".formatted(van, naar, bedrag)));
    }

    private static String json(String van, String bedrag, String naam, String iban, String omschrijving) {
        return "{\"vanIban\":\"%s\",\"bedrag\":\"%s\",\"naamOntvanger\":\"%s\",\"ibanOntvanger\":\"%s\"%s}"
                .formatted(van, bedrag, naam, iban, omschrijving == null ? "" : ",\"omschrijving\":\"" + omschrijving + "\"");
    }
}
