package nl.banksim.domain.rekening;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.IntRange;

class IbanProperties {

    @Property
    void gegenereerdeIbansZijnGeldig(@ForAll @LongRange(min = 0, max = 9_999_999_999L) long nummer) {
        Iban iban = Iban.nl("SIMB", nummer);
        assertThat(Iban.isGeldig(iban.value())).isTrue();
        assertThat(Iban.of(iban.formatted())).isEqualTo(iban);
    }

    @Property
    void eenGewijzigdCijferWordtGedetecteerd(@ForAll @LongRange(min = 0, max = 9_999_999_999L) long nummer,
                                             @ForAll @IntRange(min = 8, max = 17) int positie,
                                             @ForAll @IntRange(min = 1, max = 9) int verschil) {
        char[] tekens = Iban.nl("SIMB", nummer).value().toCharArray();
        tekens[positie] = (char) ('0' + (tekens[positie] - '0' + verschil) % 10);
        assertThat(Iban.isGeldig(new String(tekens))).isFalse();
    }
}
