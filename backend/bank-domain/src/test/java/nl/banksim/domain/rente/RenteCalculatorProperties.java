package nl.banksim.domain.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import nl.banksim.domain.geld.Money;

class RenteCalculatorProperties {

    private final RenteCalculator calculator = RenteCalculator.standaard();

    @Provide
    Arbitrary<Money> saldi() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("1000000.00")).ofScale(2).map(Money::of);
    }

    @Provide
    Arbitrary<YearMonth> maanden() {
        return Arbitraries.integers().between(0, 63).map(i -> YearMonth.of(2021, 10).plusMonths(i));
    }

    @Property
    void meerSaldoGeeftNooitMinderRente(@ForAll("saldi") Money a, @ForAll("saldi") Money b,
                                        @ForAll("maanden") YearMonth maand) {
        Money laag = Money.min(a, b);
        Money hoog = laag == a ? b : a;
        assertThat(calculator.renteVoorMaand(maand, dag -> hoog))
                .isGreaterThanOrEqualTo(calculator.renteVoorMaand(maand, dag -> laag));
    }

    @Property
    void renteIsNooitNegatiefEnHooguitDrieProcentPerJaar(@ForAll("saldi") Money saldo,
                                                         @ForAll("maanden") YearMonth maand) {
        Money rente = calculator.renteVoorMaand(maand, dag -> saldo);
        Money maximum = Money.afgerond(saldo.amount().multiply(new BigDecimal("0.03"))
                .multiply(BigDecimal.valueOf(31)).divide(BigDecimal.valueOf(365), 10, java.math.RoundingMode.UP));
        assertThat(rente.isNegative()).isFalse();
        assertThat(rente).isLessThanOrEqualTo(maximum.plus(Money.of("0.01")));
    }
}
