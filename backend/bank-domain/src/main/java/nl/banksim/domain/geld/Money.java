package nl.banksim.domain.geld;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Een geldbedrag in euro's: altijd {@link BigDecimal} met precies 2 decimalen (TO §5). Negatief is een
 * afschrijving. Bedragen met meer dan 2 decimalen worden geweigerd; afronden gebeurt alleen expliciet via
 * {@link #afgerond(BigDecimal)}.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Een bedrag heeft maximaal 2 decimalen: " + amount.toPlainString());
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    /** Leest een bedrag als {@code "1842.17"} of {@code "-63.48"}. */
    public static Money of(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new Money(new BigDecimal(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Geen geldig bedrag: " + value, e);
        }
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    /** Rondt een tussenresultaat (bijvoorbeeld rente) af op centen met bankiersafronding. */
    public static Money afgerond(BigDecimal value) {
        return new Money(value.setScale(SCALE, ROUNDING));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public Money abs() {
        return new Money(amount.abs());
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    /** Het bedrag als tekst zonder exponent, bijvoorbeeld {@code "-63.48"}; zo gaat het ook in JSON. */
    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
