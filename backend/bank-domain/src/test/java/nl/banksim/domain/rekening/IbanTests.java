package nl.banksim.domain.rekening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IbanTests {

    @Test
    void accepteertGeldigIbanMetOfZonderSpaties() {
        assertThat(Iban.of("NL91 ABNA 0417 1643 00").value()).isEqualTo("NL91ABNA0417164300");
        assertThat(Iban.of("nl91abna0417164300").formatted()).isEqualTo("NL91 ABNA 0417 1643 00");
    }

    @Test
    void weigertFouteControlecijfers() {
        assertThatThrownBy(() -> Iban.of("NL92ABNA0417164300")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Iban.isGeldig("NL92ABNA0417164300")).isFalse();
    }

    @Test
    void weigertOngeldigFormaat() {
        assertThat(Iban.isGeldig(null)).isFalse();
        assertThat(Iban.isGeldig("")).isFalse();
        assertThat(Iban.isGeldig("NL91ABNA041716430")).isFalse();
        assertThat(Iban.isGeldig("NL91AB1A0417164300")).isFalse();
        assertThat(Iban.isGeldig("DE89370400440532013000")).isTrue();
        assertThat(Iban.isGeldig("XX00" + "1".repeat(31))).isFalse();
    }

    @Test
    void maaktNederlandsIbanMetJuisteControlecijfers() {
        Iban iban = Iban.nl("SIMB", 123456789L);
        assertThat(iban.value()).startsWith("NL").endsWith("SIMB0123456789");
        assertThat(Iban.isGeldig(iban.value())).isTrue();
        assertThat(Iban.nl("ABNA", 417164300L).value()).isEqualTo("NL91ABNA0417164300");
    }

    @Test
    void weigertOngeldigeBankcodeOfNummer() {
        assertThatThrownBy(() -> Iban.nl("simb", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iban.nl("SIMB", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iban.nl("SIMB", 10_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringIsDeWaardeZonderSpaties() {
        assertThat(Iban.of("NL91 ABNA 0417 1643 00").toString()).isEqualTo("NL91ABNA0417164300");
    }

    @Test
    void laatsteBlokMagKorterZijn() {
        // Maltees voorbeeld-IBAN van 31 tekens: zeven blokken van 4 en een laatste blok van 3.
        assertThat(Iban.of("MT84MALT011000012345MTLCAST001S").formatted())
                .isEqualTo("MT84 MALT 0110 0001 2345 MTLC AST0 01S");
    }
}
