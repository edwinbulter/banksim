package nl.banksim.api.transaction;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import nl.banksim.api.account.Zoektekst;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Transactielijst met keyset-paginering op (tijdstip, id) en de zoekcriteria uit het FO. De SQL bestaat uit
 * vaste fragmenten; alle waarden gaan als parameter mee (TO §11, A05).
 */
@Repository
class TransactieRepository {

    private final JdbcClient jdbc;

    TransactieRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<TransactieRij> zoek(Iban iban, RekeningSoort soort, LocalDate totEnMet, Zoekfilter filter, Cursor na, int aantal) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id, b.boekdatum, b.transactie_tijdstip, b.tegen_iban, b.tegen_naam, b.bedrag, o.type,
                       o.uitvoer_datum, o.omschrijving, o.betalingskenmerk, o.extra_omschrijving
                FROM boeking b JOIN overboeking o ON o.id = b.overboeking_id
                WHERE b.rekening_iban = ? AND b.boekdatum <= ?
                """);
        List<Object> params = new ArrayList<>(List.of(iban.value(), totEnMet));
        if (na != null) {
            sql.append(" AND (b.transactie_tijdstip, b.id) < (?, ?)");
            params.add(Timestamp.from(na.tijdstip()));
            params.add(na.id());
        }
        if (filter.tekst() != null && !filter.tekst().isBlank()) {
            String tekst = filter.tekst().trim();
            sql.append(" AND (b.tegen_naam ILIKE ? ESCAPE '\\' OR o.omschrijving ILIKE ? ESCAPE '\\'"
                    + " OR b.tegen_iban ILIKE ? ESCAPE '\\'");
            String patroon = "%" + Zoektekst.escape(tekst) + "%";
            params.add(patroon);
            params.add(patroon);
            params.add("%" + Zoektekst.escape(tekst.replace(" ", "").toUpperCase(Locale.ROOT)) + "%");
            BigDecimal bedrag = alsBedrag(tekst);
            if (bedrag != null) {
                sql.append(" OR abs(b.bedrag) = ?");
                params.add(bedrag);
            }
            sql.append(')');
        }
        if (filter.min() != null) {
            sql.append(" AND abs(b.bedrag) >= ?");
            params.add(filter.min().abs().amount());
        }
        if (filter.max() != null) {
            sql.append(" AND abs(b.bedrag) <= ?");
            params.add(filter.max().abs().amount());
        }
        if (filter.type() != null) {
            if (soort == RekeningSoort.BETAAL && filter.type() == TransactieType.OVERSCHRIJVING) {
                // Inleg en opname heten op de betaalrekening "Overschrijving" (TO §6).
                sql.append(" AND o.type IN ('OVERSCHRIJVING', 'INLEG', 'OPNAME')");
            } else {
                sql.append(" AND o.type = ?");
                params.add(filter.type().name());
            }
        }
        if (filter.richting() == Zoekfilter.Richting.OUT) {
            sql.append(" AND b.bedrag < 0");
        } else if (filter.richting() == Zoekfilter.Richting.IN) {
            sql.append(" AND b.bedrag > 0");
        }
        sql.append(" ORDER BY b.transactie_tijdstip DESC, b.id DESC LIMIT ?");
        params.add(aantal);

        return jdbc.sql(sql.toString()).params(params)
                .query((rs, i) -> new TransactieRij(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class),
                        rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getString(5), Money.of(rs.getBigDecimal(6)),
                        TransactieType.valueOf(rs.getString(7)), rs.getObject(8, LocalDate.class), rs.getString(9),
                        rs.getString(10), rs.getString(11)))
                .list();
    }

    /** "63,48", "63.48" of "63" als bedrag, zodat de klant ook op bedrag kan zoeken. */
    static BigDecimal alsBedrag(String tekst) {
        String kandidaat = tekst.replace("€", "").replace(" ", "").replace(',', '.');
        if (!kandidaat.matches("-?[0-9]{1,13}(\\.[0-9]{1,2})?")) {
            return null;
        }
        return new BigDecimal(kandidaat).abs();
    }
}
