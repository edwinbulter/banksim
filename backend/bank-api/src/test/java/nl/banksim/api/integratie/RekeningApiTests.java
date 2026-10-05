package nl.banksim.api.integratie;

import static nl.banksim.api.integratie.Fixture.ENECO_IBAN;
import static nl.banksim.api.integratie.Fixture.JAN_BETAAL;
import static nl.banksim.api.integratie.Fixture.JAN_SPAAR;
import static nl.banksim.api.integratie.Fixture.PIET_BETAAL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RekeningApiTests extends IntegratieTest {

    @BeforeEach
    void data() {
        laadFixture();
    }

    @Test
    void overzichtToontSaldiOpDeSimulatiedatum() throws Exception {
        mvc.perform(get("/api/me/accounts").with(jan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulatiedatum").value("2026-10-05"))
                .andExpect(jsonPath("$.rekeninghouder").value("Jan de Vries"))
                .andExpect(jsonPath("$.rekeningen.length()").value(2))
                .andExpect(jsonPath("$.rekeningen[?(@.soort=='BETAAL')].saldo").value("2915.00"))
                .andExpect(jsonPath("$.rekeningen[?(@.soort=='SPAAR')].saldo").value("5010.00"));
    }

    @Test
    void betaalrekeningMetKopgegevens() throws Exception {
        mvc.perform(get("/api/accounts/{iban}", JAN_BETAAL).with(jan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rekeninghouder").value("Jan de Vries"))
                .andExpect(jsonPath("$.saldo").value("2915.00"))
                .andExpect(jsonPath("$.soort").value("BETAAL"))
                .andExpect(jsonPath("$.gekoppeldeRekening").value(JAN_SPAAR))
                .andExpect(jsonPath("$.alleenLezen").value(false))
                .andExpect(jsonPath("$.lopendeRente").doesNotExist());
    }

    @Test
    void spaarrekeningToontLopendeRente() throws Exception {
        // 5.010 × 3% / 365 × 5 dagen = 2,0589… → 2,06
        mvc.perform(get("/api/accounts/{iban}", JAN_SPAAR).with(jan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lopendeRente").value("2.06"))
                .andExpect(jsonPath("$.rentepercentage").value("3.00"));
    }

    @Test
    void ibanMetSpatiesWordtHerkend() throws Exception {
        mvc.perform(get("/api/accounts/{iban}", JAN_BETAAL.replaceAll("(.{4})", "$1 ").trim()).with(jan()))
                .andExpect(status().isOk());
    }

    @Test
    void rekeningVanEenAnderGeeft404() throws Exception {
        mvc.perform(get("/api/accounts/{iban}", PIET_BETAAL).with(jan()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://banksim.local/problems/niet-gevonden"))
                .andExpect(jsonPath("$.correlationId").exists());
        mvc.perform(get("/api/accounts/{iban}", ENECO_IBAN).with(jan())).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{iban}", "NL00SIMB0000000000").with(jan())).andExpect(status().isNotFound());
    }

    @Test
    void beheerderLeestHuishoudensAlleenLezenMaarGeenBedrijven() throws Exception {
        mvc.perform(get("/api/accounts/{iban}", JAN_BETAAL).with(beheerder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alleenLezen").value(true));
        mvc.perform(get("/api/accounts/{iban}", ENECO_IBAN).with(beheerder())).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/accounts").with(beheerder())).andExpect(status().isForbidden());
    }

    @Test
    void zonderTokenGeeft401() throws Exception {
        mvc.perform(get("/api/me/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void klantKanNietBijAdminFuncties() throws Exception {
        mvc.perform(get("/api/admin/holders").with(jan())).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/simulation-date").with(jan())
                .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void simulatiedatumBepaaltSaldoEnIsAlleenBinnenBereik() throws Exception {
        mvc.perform(put("/api/admin/simulation-date").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"2026-07-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datum").value("2026-07-15"))
                .andExpect(jsonPath("$.ingesteld").value(true));
        mvc.perform(get("/api/accounts/{iban}", JAN_BETAAL).with(jan()))
                .andExpect(jsonPath("$.saldo").value("2985.00"));

        mvc.perform(put("/api/admin/simulation-date").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"2030-01-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/simulation-date").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingesteld").value(false));
    }

    @Test
    void beheerderZietRekeninghoudersMetSaldi() throws Exception {
        mvc.perform(get("/api/admin/holders").with(beheerder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].naam").value("Jan de Vries"))
                .andExpect(jsonPath("$[0].saldoBetaal").value("2915.00"))
                .andExpect(jsonPath("$[0].saldoSpaar").value("5010.00"));
        mvc.perform(get("/api/admin/holders").param("q", "piet").with(beheerder()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/holders/{id}/accounts", Fixture.JAN).with(beheerder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rekeningen.length()").value(2));
        mvc.perform(get("/api/admin/holders/{id}/accounts", Fixture.ENECO).with(beheerder()))
                .andExpect(status().isNotFound());
    }
}
