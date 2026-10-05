package nl.banksim.datagen.generator;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

import nl.banksim.domain.geld.Money;

/**
 * Deterministisch toeval op basis van {@link Random} (algoritme vastgelegd in de Java-specificatie, dus
 * dezelfde reeks op elke JVM). Bedragen worden in centen getrokken, nooit als floating point.
 */
final class Toeval {

    private final Random random;

    Toeval(long seed) {
        this.random = new Random(seed);
    }

    /** Kans van gemiddeld {@code perMaand} keer per maand, vermenigvuldigd met {@code procent}/100. */
    boolean perMaand(int perMaand, int procent) {
        return random.nextInt(3000) < perMaand * procent;
    }

    boolean procent(int procent) {
        return random.nextInt(100) < procent;
    }

    int tussen(int min, int maxInclusief) {
        return min + random.nextInt(maxInclusief - min + 1);
    }

    Money bedrag(String min, String max) {
        long van = Money.of(min).amount().movePointRight(2).longValueExact();
        long tot = Money.of(max).amount().movePointRight(2).longValueExact();
        return Money.of(BigDecimal.valueOf(van + (long) random.nextInt((int) (tot - van + 1)), 2));
    }

    /** Een veelvoud van {@code stap} euro tussen min en max. */
    Money ronde(int min, int max, int stap) {
        return Money.of(BigDecimal.valueOf((long) tussen(min / stap, max / stap) * stap));
    }

    LocalTime tijd(int vanUur, int totUur) {
        return LocalTime.of(tussen(vanUur, totUur - 1), random.nextInt(60));
    }

    <T> T kies(List<T> opties) {
        return opties.get(random.nextInt(opties.size()));
    }

    String cijfers(int aantal) {
        StringBuilder sb = new StringBuilder();
        sb.append(1 + random.nextInt(9));
        for (int i = 1; i < aantal; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
