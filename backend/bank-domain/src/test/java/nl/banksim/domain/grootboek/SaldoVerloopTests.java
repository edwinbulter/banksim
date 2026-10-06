package nl.banksim.domain.grootboek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;

class SaldoVerloopTests {

    static final LocalDate D = LocalDate.of(2026, 10, 5);
    static final Iban REKENING = Iban.nl("SIMB", 1);

    @Test
    void saldoOpEenDatumTeltAlleenMutatiesTotEnMetDieDag() {
        List<Mutatie> mutaties = List.of(
                new Mutatie(D.minusDays(1), Money.of("100.00")),
                new Mutatie(D, Money.of("-30.00")),
                new Mutatie(D.plusDays(1), Money.of("-50.00")));
        assertThat(SaldoVerloop.saldoOp(Money.of("10.00"), mutaties, D)).isEqualTo(Money.of("80.00"));
        assertThat(SaldoVerloop.saldoOp(Money.of("10.00"), mutaties, D.minusDays(2))).isEqualTo(Money.of("10.00"));
    }

    @Test
    void laagsteEindsaldoKijktNaarDeToekomst() {
        List<Mutatie> mutaties = List.of(
                new Mutatie(D, Money.of("-40.00")),
                new Mutatie(D.plusDays(10), Money.of("-70.00")),
                new Mutatie(D.plusDays(20), Money.of("500.00")));
        assertThat(SaldoVerloop.laagsteEindsaldo(Money.of("100.00"), mutaties)).isEqualTo(Money.of("-10.00"));
    }

    @Test
    void volgordeBinnenEenDagTeltNiet() {
        List<Mutatie> mutaties = List.of(
                new Mutatie(D, Money.of("-150.00")),
                new Mutatie(D, Money.of("200.00")));
        assertThat(SaldoVerloop.laagsteEindsaldo(Money.of("100.00"), mutaties)).isEqualTo(Money.of("100.00"));
    }

    @Test
    void zonderMutatiesIsHetLaagsteSaldoHetBeginsaldo() {
        assertThat(SaldoVerloop.laagsteEindsaldo(Money.of("5.00"), List.of())).isEqualTo(Money.of("5.00"));
    }

    @Test
    void nooitRoodWeigertEenBetalingDieLaterEenTekortGeeft() {
        // Betaling van 60 op D past vandaag, maar de huur van 50 over tien dagen dan niet meer.
        List<Mutatie> metBetaling = List.of(
                new Mutatie(D, Money.of("-60.00")),
                new Mutatie(D.plusDays(10), Money.of("-50.00")));
        assertThatThrownBy(() -> NooitRoodRegel.controleer(REKENING, RekeningSoort.BETAAL, Money.of("100.00"), metBetaling))
                .isInstanceOf(SaldoOntoereikendException.class)
                .satisfies(e -> assertThat(((SaldoOntoereikendException) e).rekening()).isEqualTo(REKENING));
    }

    @Test
    void nooitRoodStaatPreciesNulToe() {
        List<Mutatie> mutaties = List.of(new Mutatie(D, Money.of("-100.00")));
        assertThatNoException().isThrownBy(
                () -> NooitRoodRegel.controleer(REKENING, RekeningSoort.SPAAR, Money.of("100.00"), mutaties));
    }

    @Test
    void externeRekeningenMogenNegatiefWorden() {
        List<Mutatie> mutaties = List.of(new Mutatie(D, Money.of("-1000.00")));
        assertThatNoException().isThrownBy(
                () -> NooitRoodRegel.controleer(REKENING, RekeningSoort.EXTERN, Money.ZERO, mutaties));
        assertThat(RekeningSoort.EXTERN.magNietRoodStaan()).isFalse();
    }
}
