package nl.banksim.api.account;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;

/** Alleen geparametriseerde queries (TO §11, A05). */
@Repository
class RekeningRepository {

    private static final String SELECT = """
            SELECT r.iban, r.soort, r.openingssaldo, h.id AS houder_id, h.naam, h.soort AS houder_soort, h.keycloak_sub
            FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
            """;

    private final JdbcClient jdbc;

    RekeningRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Rekening> vind(Iban iban) {
        return jdbc.sql(SELECT + " WHERE r.iban = ?").param(iban.value()).query(RekeningRepository::rekening).optional();
    }

    List<Rekening> vanHouder(UUID houderId) {
        return jdbc.sql(SELECT + " WHERE h.id = ? ORDER BY r.soort, r.iban").param(houderId)
                .query(RekeningRepository::rekening).list();
    }

    Optional<UUID> houderMetSub(String sub) {
        return jdbc.sql("SELECT id FROM rekeninghouder WHERE keycloak_sub = ?").param(sub).query(UUID.class).optional();
    }

    Optional<String> huishoudenNaam(UUID houderId) {
        return jdbc.sql("SELECT naam FROM rekeninghouder WHERE id = ? AND soort = 'HUISHOUDEN'").param(houderId)
                .query(String.class).optional();
    }

    /** Saldo aan het eind van {@code datum}: openingssaldo plus alle boekingen t/m die dag. */
    Money saldo(Iban iban, LocalDate datum) {
        BigDecimal saldo = jdbc.sql("""
                SELECT r.openingssaldo + COALESCE((SELECT sum(b.bedrag) FROM boeking b
                                                   WHERE b.rekening_iban = r.iban AND b.boekdatum <= ?), 0)
                FROM rekening r WHERE r.iban = ?
                """).params(datum, iban.value()).query(BigDecimal.class).single();
        return Money.of(saldo);
    }

    List<Huishouden> huishoudens(String zoek) {
        String patroon = zoek == null || zoek.isBlank() ? "%" : "%" + Zoektekst.escape(zoek.trim()) + "%";
        return jdbc.sql("""
                SELECT h.id, h.naam,
                       max(r.iban) FILTER (WHERE r.soort = 'BETAAL') AS betaal,
                       max(r.iban) FILTER (WHERE r.soort = 'SPAAR') AS spaar
                FROM rekeninghouder h JOIN rekening r ON r.rekeninghouder_id = h.id
                WHERE h.soort = 'HUISHOUDEN' AND h.naam ILIKE ? ESCAPE '\\'
                GROUP BY h.id, h.naam ORDER BY h.naam
                """).param(patroon)
                .query((rs, i) -> new Huishouden(rs.getObject(1, UUID.class), rs.getString(2),
                        iban(rs.getString(3)), iban(rs.getString(4))))
                .list();
    }

    private static Iban iban(String waarde) {
        return waarde == null ? null : Iban.of(waarde);
    }

    private static Rekening rekening(ResultSet rs, int rij) throws SQLException {
        return new Rekening(Iban.of(rs.getString("iban")), RekeningSoort.valueOf(rs.getString("soort")),
                Money.of(rs.getBigDecimal("openingssaldo")), rs.getObject("houder_id", UUID.class),
                rs.getString("naam"), rs.getString("houder_soort"), rs.getString("keycloak_sub"));
    }
}
