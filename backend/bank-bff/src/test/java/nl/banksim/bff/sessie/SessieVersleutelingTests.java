package nl.banksim.bff.sessie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SessieVersleutelingTests {

    private final SessieVersleuteling versleuteling =
            new SessieVersleuteling("test-sessiesleutel-van-minstens-32-tekens", getClass().getClassLoader());

    @Test
    void heenEnTerug() {
        Map<String, String> tokens = new HashMap<>(Map.of("access_token", "eyJhbGciOiJSUzI1NiJ9.geheim"));
        assertThat(versleuteling.ontsleutel(versleuteling.versleutel(tokens))).isEqualTo(tokens);
    }

    @Test
    void opgeslagenBytesBevattenGeenLeesbareTokensOfKlassenamen() {
        byte[] opgeslagen = versleuteling.versleutel(new HashMap<>(Map.of("access_token", "eyJhbGciOiJSUzI1NiJ9.geheim")));
        String tekst = new String(opgeslagen, StandardCharsets.ISO_8859_1);
        assertThat(tekst).doesNotContain("eyJ").doesNotContain("geheim").doesNotContain("java.util");
    }

    @Test
    void elkeVersleutelingIsAnders() {
        assertThat(versleuteling.versleutel("x")).isNotEqualTo(versleuteling.versleutel("x"));
    }

    @Test
    void gemanipuleerdeOfVreemdeBytesWordenGenegeerd() {
        byte[] opgeslagen = versleuteling.versleutel("waarde");
        opgeslagen[opgeslagen.length - 1] ^= 1;
        assertThat(versleuteling.ontsleutel(opgeslagen)).isNull();
        assertThat(versleuteling.ontsleutel(new byte[] {9, 1, 2})).isNull();
        var andereSleutel = new SessieVersleuteling("een-heel-andere-sleutel-van-32-tekens!!", getClass().getClassLoader());
        assertThat(andereSleutel.ontsleutel(versleuteling.versleutel("waarde"))).isNull();
    }

    @Test
    void sleutelMoetLangGenoegZijn() {
        assertThatThrownBy(() -> new SessieVersleuteling("kort", getClass().getClassLoader()))
                .isInstanceOf(IllegalStateException.class);
    }
}
