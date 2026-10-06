package nl.banksim.domain.geld;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MoneyProperties {

    @Provide
    Arbitrary<Money> bedragen() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000000.00"), new BigDecimal("1000000000.00"))
                .ofScale(2)
                .map(Money::of);
    }

    @Property
    void optellenEnAftrekkenHeffenElkaarOp(@ForAll("bedragen") Money a, @ForAll("bedragen") Money b) {
        assertThat(a.plus(b).minus(b)).isEqualTo(a);
    }

    @Property
    void optellenIsCommutatief(@ForAll("bedragen") Money a, @ForAll("bedragen") Money b) {
        assertThat(a.plus(b)).isEqualTo(b.plus(a));
    }

    @Property
    void resultaatHeeftAltijdTweeDecimalen(@ForAll("bedragen") Money a, @ForAll("bedragen") Money b) {
        assertThat(a.plus(b).amount().scale()).isEqualTo(2);
        assertThat(a.minus(b).amount().scale()).isEqualTo(2);
    }

    @Property
    void tekstweergaveIsTerugTeLezen(@ForAll("bedragen") Money a) {
        assertThat(Money.of(a.toString())).isEqualTo(a);
    }

    @Property
    void negatieveVanNegatiefIsOrigineel(@ForAll("bedragen") Money a) {
        assertThat(a.negate().negate()).isEqualTo(a);
        assertThat(a.plus(a.negate()).isZero()).isTrue();
    }
}
