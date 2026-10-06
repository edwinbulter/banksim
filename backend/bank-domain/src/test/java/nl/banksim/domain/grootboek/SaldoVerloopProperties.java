package nl.banksim.domain.grootboek;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import nl.banksim.domain.geld.Money;

class SaldoVerloopProperties {

    static final LocalDate START = LocalDate.of(2021, 10, 1);

    @Provide
    Arbitrary<List<Mutatie>> mutaties() {
        Arbitrary<LocalDate> datum = Arbitraries.integers().between(0, 1900).map(START::plusDays);
        Arbitrary<Money> bedrag = Arbitraries.bigDecimals()
                .between(new BigDecimal("-5000.00"), new BigDecimal("5000.00")).ofScale(2).map(Money::of);
        return Combinators.combine(datum, bedrag).as(Mutatie::new).list().ofMaxSize(60);
    }

    @Property
    void laagsteEindsaldoIsNietHogerDanEenEindsaldo(@ForAll("mutaties") List<Mutatie> mutaties) {
        Money beginsaldo = Money.of("250.00");
        Money laagste = SaldoVerloop.laagsteEindsaldo(beginsaldo, mutaties);
        assertThat(laagste).isLessThanOrEqualTo(beginsaldo);
        for (Mutatie mutatie : mutaties) {
            assertThat(laagste).isLessThanOrEqualTo(SaldoVerloop.saldoOp(beginsaldo, mutaties, mutatie.datum()));
        }
    }

    @Property
    void eindsaldoNaAlleMutatiesIsSomVanAlles(@ForAll("mutaties") List<Mutatie> mutaties) {
        Money som = mutaties.stream().map(Mutatie::bedrag).reduce(Money.ZERO, Money::plus);
        assertThat(SaldoVerloop.saldoOp(Money.ZERO, mutaties, START.plusDays(2000))).isEqualTo(som);
    }
}
