package nl.banksim.domain.rekening;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Een gevalideerd IBAN (ISO 13616, mod-97). Spaties worden genegeerd; de waarde is altijd in hoofdletters
 * zonder spaties. BankSim gebruikt Nederlandse IBAN's (18 tekens).
 */
public record Iban(String value) {

    private static final Pattern FORMAAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    private static final Pattern NL_FORMAAT = Pattern.compile("NL[0-9]{2}[A-Z]{4}[0-9]{10}");
    private static final BigInteger NEGENENNEGENTIG_ZEVEN = BigInteger.valueOf(97);

    public Iban {
        Objects.requireNonNull(value, "value");
        value = normaliseer(value);
        if (!isGeldig(value)) {
            throw new IllegalArgumentException("Ongeldig IBAN: " + value);
        }
    }

    public static Iban of(String value) {
        return new Iban(value);
    }

    /** Maakt een Nederlands IBAN met juiste controlecijfers, bijvoorbeeld {@code nl("SIMB", 123456789)}. */
    public static Iban nl(String bankcode, long rekeningnummer) {
        if (!bankcode.matches("[A-Z]{4}") || rekeningnummer < 0 || rekeningnummer > 9_999_999_999L) {
            throw new IllegalArgumentException("Ongeldige bankcode of rekeningnummer");
        }
        String bban = bankcode + String.format("%010d", rekeningnummer);
        int controle = 98 - mod97(bban + "NL00");
        return new Iban(String.format("NL%02d%s", controle, bban));
    }

    public static boolean isGeldig(String kandidaat) {
        if (kandidaat == null) {
            return false;
        }
        String iban = normaliseer(kandidaat);
        if (!FORMAAT.matcher(iban).matches() || iban.length() > 34) {
            return false;
        }
        if (iban.startsWith("NL") && !NL_FORMAAT.matcher(iban).matches()) {
            return false;
        }
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
    }

    /** Weergave in blokken van 4, zoals {@code NL12 SIMB 0123 4567 89}. */
    public String formatted() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i += 4) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(value, i, Math.min(i + 4, value.length()));
        }
        return sb.toString();
    }

    /** Voor logs: alleen landcode, controlecijfers en de laatste 4 tekens, zoals {@code NL13…0011}. */
    public String gemaskeerd() {
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return value;
    }

    private static String normaliseer(String value) {
        return value.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static int mod97(String tekens) {
        StringBuilder cijfers = new StringBuilder();
        for (char c : tekens.toCharArray()) {
            cijfers.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(cijfers.toString()).mod(NEGENENNEGENTIG_ZEVEN).intValue();
    }
}
