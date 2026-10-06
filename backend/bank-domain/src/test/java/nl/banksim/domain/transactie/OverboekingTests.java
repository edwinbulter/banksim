package nl.banksim.domain.transactie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;

class OverboekingTests {

    static final Partij JAN = new Partij(Iban.nl("SIMB", 1), "J. de Vries");
    static final Partij ENECO = new Partij(Iban.nl("SIMB", 9001), "Eneco");

    @Test
    void levertTweeBoekingenDieSamenNulZijn() {
        List<Boeking> boekingen = overboeking(TransactieType.INCASSO, "148.00").boekingen();

        assertThat(boekingen).hasSize(2);
        Boeking af = boekingen.get(0);
        Boeking bij = boekingen.get(1);
        assertThat(af.rekening()).isEqualTo(JAN.iban());
        assertThat(af.bedrag()).isEqualTo(Money.of("-148.00"));
        assertThat(af.tegenIban()).isEqualTo(ENECO.iban());
        assertThat(af.tegenNaam()).isEqualTo("Eneco");
        assertThat(af.isAfschrijving()).isTrue();
        assertThat(bij.rekening()).isEqualTo(ENECO.iban());
        assertThat(bij.tegenNaam()).isEqualTo("J. de Vries");
        assertThat(af.bedrag().plus(bij.bedrag()).isZero()).isTrue();
    }

    @Test
    void betaalautomaatEnGeldautomaatHebbenGeenTegenIban() {
        assertThat(overboeking(TransactieType.BETAALAUTOMAAT, "63.48").boekingen())
                .allSatisfy(boeking -> assertThat(boeking.tegenIban()).isNull());
        assertThat(overboeking(TransactieType.GELDAUTOMAAT, "50.00").boekingen())
                .allSatisfy(boeking -> assertThat(boeking.tegenIban()).isNull());
    }

    @Test
    void weigertNulOfNegatiefBedrag() {
        assertThatThrownBy(() -> overboeking(TransactieType.INCASSO, "0.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> overboeking(TransactieType.INCASSO, "-1.00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weigertOverboekingNaarZichzelf() {
        assertThatThrownBy(() -> new Overboeking(UUID.randomUUID(), TransactieType.OVERSCHRIJVING, JAN, JAN,
                Money.of("1.00"), Instant.now(), LocalDate.now(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bewaaktMaximaleLengtesUitHetFo() {
        assertThat(metTeksten("x".repeat(140), "x".repeat(25), "x".repeat(35)).omschrijving()).hasSize(140);
        assertThatThrownBy(() -> metTeksten("x".repeat(141), null, null)).hasMessageContaining("140");
        assertThatThrownBy(() -> metTeksten(null, "x".repeat(26), null)).hasMessageContaining("25");
        assertThatThrownBy(() -> metTeksten(null, null, "x".repeat(36))).hasMessageContaining("35");
        assertThat(metTeksten(" ", "", null).omschrijving()).isNull();
    }

    @Test
    void naamIsVerplichtEnMaximaal70Tekens() {
        assertThatThrownBy(() -> new Partij(JAN.iban(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Partij(JAN.iban(), "x".repeat(71))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Partij(JAN.iban(), "x".repeat(70)).naam()).hasSize(70);
    }

    @Test
    void labelsVolgensHetFo() {
        assertThat(TransactieType.IDEAL_WERO.label()).isEqualTo("iDEAL | Wero");
        assertThat(TransactieType.VERZAMELBETALING.isBetaalrekeningType()).isTrue();
        assertThat(TransactieType.RENTE.isBetaalrekeningType()).isFalse();
    }

    @Test
    void bijschrijvingIsGeenAfschrijving() {
        assertThat(overboeking(TransactieType.INCASSO, "1.00").boekingen().get(1).isAfschrijving()).isFalse();
    }

    private static Overboeking overboeking(TransactieType type, String bedrag) {
        return new Overboeking(UUID.randomUUID(), type, JAN, ENECO, Money.of(bedrag),
                Instant.parse("2026-10-01T04:02:00Z"), LocalDate.of(2026, 10, 1), "Energie oktober", null, null);
    }

    private static Overboeking metTeksten(String omschrijving, String kenmerk, String extra) {
        return new Overboeking(UUID.randomUUID(), TransactieType.ONLINE_BANKIEREN, JAN, ENECO, Money.of("1.00"),
                Instant.now(), LocalDate.now(), omschrijving, kenmerk, extra);
    }
}
