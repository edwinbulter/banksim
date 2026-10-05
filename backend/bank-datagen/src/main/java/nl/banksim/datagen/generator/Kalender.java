package nl.banksim.datagen.generator;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Werkdagen, inflatie en seizoenen. Alleen weekenden tellen als vrije dag. */
public final class Kalender {

    private static final DateTimeFormatter MAAND = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("nl"));

    private Kalender() {
    }

    public static boolean isWerkdag(LocalDate dag) {
        return dag.getDayOfWeek() != DayOfWeek.SATURDAY && dag.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Eerste werkdag op of na de gegeven dag van de maand van {@code dag}. */
    public static LocalDate werkdagOpOfNa(LocalDate dag, int dagVanMaand) {
        LocalDate kandidaat = dag.withDayOfMonth(Math.min(dagVanMaand, dag.lengthOfMonth()));
        while (!isWerkdag(kandidaat)) {
            kandidaat = kandidaat.plusDays(1);
        }
        return kandidaat;
    }

    /** Laatste werkdag op of vóór de gegeven dag van de maand van {@code dag}. */
    public static LocalDate werkdagOpOfVoor(LocalDate dag, int dagVanMaand) {
        LocalDate kandidaat = dag.withDayOfMonth(Math.min(dagVanMaand, dag.lengthOfMonth()));
        while (!isWerkdag(kandidaat)) {
            kandidaat = kandidaat.minusDays(1);
        }
        return kandidaat;
    }

    public static boolean isOp(LocalDate dag, LocalDate gepland) {
        return dag.equals(gepland);
    }

    public static String maand(LocalDate dag) {
        return MAAND.format(dag);
    }

    /** Prijsniveau ten opzichte van 2021 (afgerond op CBS-cijfers). */
    public static BigDecimal inflatie(int jaar) {
        return switch (jaar) {
            case 2021 -> new BigDecimal("1.00");
            case 2022 -> new BigDecimal("1.10");
            case 2023 -> new BigDecimal("1.16");
            case 2024 -> new BigDecimal("1.20");
            case 2025 -> new BigDecimal("1.24");
            default -> new BigDecimal("1.28");
        };
    }

    /** Lonen en uitkeringen ten opzichte van 2021. */
    public static BigDecimal loonstijging(int jaar) {
        return switch (jaar) {
            case 2021 -> new BigDecimal("1.00");
            case 2022 -> new BigDecimal("1.04");
            case 2023 -> new BigDecimal("1.12");
            case 2024 -> new BigDecimal("1.18");
            case 2025 -> new BigDecimal("1.22");
            default -> new BigDecimal("1.26");
        };
    }

    /** Energieprijzen: de piek van 2022 en de langzame daling daarna. */
    public static BigDecimal energie(int jaar) {
        return switch (jaar) {
            case 2021 -> new BigDecimal("1.00");
            case 2022 -> new BigDecimal("1.80");
            case 2023 -> new BigDecimal("1.50");
            case 2024 -> new BigDecimal("1.25");
            default -> new BigDecimal("1.20");
        };
    }
}
