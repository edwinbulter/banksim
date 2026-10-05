package nl.banksim.api.integratie;

import static nl.banksim.api.integratie.Fixture.JAN_BETAAL;
import static nl.banksim.api.integratie.Fixture.JAN_SPAAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactieApiTests extends IntegratieTest {

    @BeforeEach
    void data() {
        laadFixture();
    }

    @Test
    void eerste50NieuwsteEersteEnDaarnaDeRest() throws Exception {
        String eerste = mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).with(jan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.items[0].datum").value("2026-09-01"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(eerste, "$.nextCursor");

        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("cursor", cursor).with(jan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(11))
                .andExpect(jsonPath("$.items[10].datum").value("2026-07-01"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void transactiesNaDeSimulatiedatumZijnOnzichtbaar() throws Exception {
        String json = mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("q", "jaarafrekening").with(jan()))
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(json, "$.items.length()")).isZero();
    }

    @Test
    void gemanipuleerdeCursorGeeft400() throws Exception {
        String json = mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).with(jan()))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(json, "$.nextCursor");
        String vervalst = "X" + cursor.substring(1);
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("cursor", vervalst).with(jan()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("cursor", "onzin").with(jan()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uitklapdetailsVolgensHetFo() throws Exception {
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("type", "INCASSO").param("size", "1").with(jan()))
                .andExpect(jsonPath("$.items[0].typeLabel").value("Incasso"))
                .andExpect(jsonPath("$.items[0].rekeninghouder").value("Jan de Vries"))
                .andExpect(jsonPath("$.items[0].bedrag").value("-1.00"))
                .andExpect(jsonPath("$.items[0].van.naam").value("Jan de Vries"))
                .andExpect(jsonPath("$.items[0].van.iban").value(JAN_BETAAL))
                .andExpect(jsonPath("$.items[0].naar.naam").value("Eneco"))
                .andExpect(jsonPath("$.items[0].naar.iban").value(Fixture.ENECO_IBAN))
                .andExpect(jsonPath("$.items[0].uitgevoerdOp").value("2026-08-29"))
                .andExpect(jsonPath("$.items[0].tijdstip").exists());
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("type", "BETAALAUTOMAAT").with(jan()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].naar.naam").value("Eneco winkel Utrecht"))
                .andExpect(jsonPath("$.items[0].naar.iban").doesNotExist())
                .andExpect(jsonPath("$.items[0].tegenIban").doesNotExist());
    }

    @Test
    void zoekenOpNaamBedragIbanEnOmschrijving() throws Exception {
        aantal("q", "winkel", 1);
        aantal("q", "25,00", 1);
        aantal("q", "€ 25", 1);
        aantal("q", "termijn 1", 11);
        aantal("q", Fixture.ENECO_IBAN.substring(0, 8), 60);
        aantal("q", "100%", 0);
    }

    @Test
    void filterOpBedragTypeEnRichting() throws Exception {
        aantal("min", "20", 1);
        aantal("max", "1", 60);
        aantal("direction", "IN", 0);
        aantal("direction", "OUT", 61);
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("min", "30").param("max", "20").with(jan()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param("min", "1,5").with(jan()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void spaarrekeningToontRente() throws Exception {
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_SPAAR).with(jan()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("RENTE"))
                .andExpect(jsonPath("$.items[0].typeLabel").value("Rente"))
                .andExpect(jsonPath("$.items[0].bedrag").value("10.00"));
    }

    @Test
    void geenToegangTotTransactiesVanEenAnder() throws Exception {
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).with(piet())).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).with(beheerder())).andExpect(status().isOk());
    }

    private void aantal(String parameter, String waarde, int verwacht) throws Exception {
        mvc.perform(get("/api/accounts/{iban}/transactions", JAN_BETAAL).param(parameter, waarde).param("size", "50").with(jan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(Math.min(verwacht, 50)));
    }
}
