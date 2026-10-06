package nl.banksim.api.simulation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * De "huidige" datum van de simulatie (TO §8): ingesteld door de beheerder, of anders de systeemdatum binnen
 * het bereik van de data. Vervangt overal {@code LocalDate.now()}; kort gecachet.
 */
@Component
public class SimulationClock {

    public static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
    private static final long CACHE_MILLIS = 5_000;

    private final JdbcClient jdbc;
    private final Clock klok;
    private final AtomicReference<Gecachet> cache = new AtomicReference<>();

    public SimulationClock(JdbcClient jdbc, Clock klok) {
        this.jdbc = jdbc;
        this.klok = klok;
    }

    /** De simulatiedatum D. */
    public LocalDate vandaag() {
        Instelling instelling = instelling();
        if (instelling.ingesteld() != null) {
            return instelling.ingesteld();
        }
        LocalDate systeem = LocalDate.now(klok.withZone(ZONE));
        if (systeem.isBefore(instelling.vanaf())) {
            return instelling.vanaf();
        }
        return systeem.isAfter(instelling.tot()) ? instelling.tot() : systeem;
    }

    /** Tijdstip voor nieuwe boekingen: de simulatiedatum met de huidige kloktijd. */
    public Instant nu() {
        return vandaag().atTime(LocalTime.now(klok.withZone(ZONE))).atZone(ZONE).toInstant();
    }

    public Instelling instelling() {
        Gecachet gecachet = cache.get();
        long nu = klok.millis();
        if (gecachet == null || nu - gecachet.geladen() > CACHE_MILLIS) {
            Instelling instelling = jdbc.sql("SELECT simulatiedatum, data_vanaf, data_tot FROM instelling WHERE id = 1")
                    .query((rs, i) -> new Instelling(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class),
                            rs.getObject(3, LocalDate.class)))
                    .single();
            gecachet = new Gecachet(instelling, nu);
            cache.set(gecachet);
        }
        return gecachet.instelling();
    }

    /** Alleen voor de beheerder; {@code null} = terug naar de systeemdatum. */
    public void zet(LocalDate datum) {
        jdbc.sql("UPDATE instelling SET simulatiedatum = ? WHERE id = 1").param(datum).update();
        herlaad();
    }

    /** Vergeet de gecachete instelling; de volgende aanroep leest de database. */
    public void herlaad() {
        cache.set(null);
    }

    /** @param ingesteld door de beheerder gekozen datum, of {@code null} */
    public record Instelling(LocalDate ingesteld, LocalDate vanaf, LocalDate tot) {
    }

    private record Gecachet(Instelling instelling, long geladen) {
    }
}
