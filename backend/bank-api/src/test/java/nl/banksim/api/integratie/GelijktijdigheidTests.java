package nl.banksim.api.integratie;

import static nl.banksim.api.integratie.Fixture.JAN_BETAAL;
import static nl.banksim.api.integratie.Fixture.PIET_BETAAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** TO §13: parallelle overboekingen kriskras geven geen deadlocks, geen negatief saldo en geen verloren geld. */
class GelijktijdigheidTests extends IntegratieTest {

    @BeforeEach
    void data() {
        laadFixture();
    }

    @Test
    void parallelleBetalingenInBeideRichtingen() throws Exception {
        BigDecimal totaalVoor = totaal();
        List<Callable<Integer>> taken = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            boolean vanJan = i % 2 == 0;
            String bedrag = vanJan ? "15.00" : "40.00"; // Piet heeft 500: hij raakt halverwege door zijn saldo heen
            taken.add(() -> mvc.perform(post("/api/payments").with(vanJan ? jan() : piet())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"vanIban\":\"%s\",\"bedrag\":\"%s\",\"naamOntvanger\":\"%s\",\"ibanOntvanger\":\"%s\"}"
                                    .formatted(vanJan ? JAN_BETAAL : PIET_BETAAL, bedrag,
                                            vanJan ? "Piet Pieters" : "Jan de Vries", vanJan ? PIET_BETAAL : JAN_BETAAL)))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statussen = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (Future<Integer> f : executor.invokeAll(taken)) {
                statussen.add(f.get());
            }
        }

        assertThat(statussen).allMatch(s -> s == 201 || s == 422);
        assertThat(statussen).contains(201);
        assertThat(totaal()).isEqualByComparingTo(totaalVoor);
        assertThat(saldo(PIET_BETAAL)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(saldo(JAN_BETAAL)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(beheer.queryForObject("SELECT COALESCE(sum(bedrag), 0) FROM boeking", BigDecimal.class)).isZero();
    }

    private BigDecimal totaal() {
        return saldo(JAN_BETAAL).add(saldo(PIET_BETAAL));
    }

    private BigDecimal saldo(String iban) {
        return beheer.queryForObject("""
                SELECT r.openingssaldo + COALESCE((SELECT sum(bedrag) FROM boeking WHERE rekening_iban = r.iban), 0)
                FROM rekening r WHERE iban = ?
                """, BigDecimal.class, iban);
    }
}
