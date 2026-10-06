package nl.banksim.datagen.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Controles na het schrijven (TO §9): het grootboek klopt en geen enkele klantrekening stond ooit rood. */
@Component
public class Invarianten {

    private final JdbcTemplate jdbc;

    public Invarianten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void controleer() {
        Integer somNietNul = jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM boeking HAVING sum(bedrag) <> 0) x",
                Integer.class);
        if (somNietNul != null && somNietNul > 0) {
            throw new IllegalStateException("Som van alle boekingen is niet nul");
        }
        Integer negatief = jdbc.queryForObject("""
                WITH per_dag AS (
                    SELECT rekening_iban, boekdatum, sum(bedrag) AS mutatie
                    FROM boeking GROUP BY rekening_iban, boekdatum
                ), verloop AS (
                    SELECT d.rekening_iban,
                           r.openingssaldo + sum(d.mutatie) OVER (PARTITION BY d.rekening_iban ORDER BY d.boekdatum) AS saldo
                    FROM per_dag d JOIN rekening r ON r.iban = d.rekening_iban
                    WHERE r.soort IN ('BETAAL', 'SPAAR')
                )
                SELECT count(*) FROM verloop WHERE saldo < 0
                """, Integer.class);
        if (negatief != null && negatief > 0) {
            throw new IllegalStateException(negatief + " dagen met een negatief saldo op een klantrekening");
        }
    }
}
