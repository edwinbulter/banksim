package nl.banksim.api.contact;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import nl.banksim.api.account.Zoektekst;
import nl.banksim.domain.rekening.Iban;

/** Rekeningen waarnaar betaald mag worden (FO: alleen bestaande rekeningen uit de fake data). */
@Service
public class ContactService {

    private final JdbcClient jdbc;

    ContactService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Contact> vind(Iban iban) {
        return jdbc.sql("SELECT iban, naam FROM contact WHERE iban = ?").param(iban.value())
                .query((rs, i) -> new Contact(Iban.of(rs.getString(1)), rs.getString(2))).optional();
    }

    /** Suggesties op naam of IBAN, zonder de eigen rekeningen van de klant. */
    List<Contact> zoek(String tekst, String eigenSub) {
        String naam = "%" + Zoektekst.escape(tekst.trim()) + "%";
        String iban = Zoektekst.escape(tekst.replace(" ", "").toUpperCase(Locale.ROOT)) + "%";
        return jdbc.sql("""
                SELECT c.iban, c.naam FROM contact c
                JOIN rekening r ON r.iban = c.iban JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                WHERE (c.naam ILIKE ? ESCAPE '\\' OR c.iban LIKE ? ESCAPE '\\')
                  AND h.keycloak_sub IS DISTINCT FROM ?
                ORDER BY c.naam LIMIT 10
                """).params(naam, iban, eigenSub)
                .query((rs, i) -> new Contact(Iban.of(rs.getString(1)), rs.getString(2))).list();
    }

    public record Contact(Iban iban, String naam) {

        /** De naam van de ontvanger moet bij het IBAN passen (hoofdletters en extra spaties tellen niet). */
        public boolean naamPast(String opgegeven) {
            return normaliseer(naam).equals(normaliseer(opgegeven));
        }

        private static String normaliseer(String tekst) {
            return tekst == null ? "" : tekst.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
    }
}
