package nl.banksim.api.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import nl.banksim.api.common.Fout;
import tools.jackson.databind.json.JsonMapper;

/**
 * Een betaalverzoek met dezelfde Idempotency-Key wordt maar één keer geboekt (TO §6). De sleutel wordt eerst
 * vastgelegd; een gelijktijdig tweede verzoek wacht op de eerste transactie en krijgt daarna hetzelfde
 * antwoord. Mislukt de boeking, dan verdwijnt de sleutel mee met de rollback en mag de client het opnieuw
 * proberen.
 */
@Component
class Idempotency {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Idempotency(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    <T> T eenmalig(UUID sleutel, UUID rekeninghouder, String verzoek, Class<T> type, Supplier<T> actie) {
        String hash = sha256(verzoek);
        int nieuw = jdbc.sql("""
                INSERT INTO idempotency_key (sleutel, rekeninghouder_id, request_hash, response_status)
                VALUES (?, ?, ?, 0) ON CONFLICT DO NOTHING
                """).params(sleutel, rekeninghouder, hash).update();
        if (nieuw == 0) {
            var eerder = jdbc.sql("""
                    SELECT request_hash, response_body::text FROM idempotency_key WHERE sleutel = ? AND rekeninghouder_id = ?
                    """).params(sleutel, rekeninghouder)
                    .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)}).single();
            if (!eerder[0].equals(hash)) {
                throw Fout.bedrijfsregel("idempotency-key-hergebruikt", "Sleutel al gebruikt",
                        "Deze Idempotency-Key is al gebruikt voor een ander verzoek.");
            }
            return json.readValue(eerder[1], type);
        }
        T antwoord = actie.get();
        jdbc.sql("""
                UPDATE idempotency_key SET response_status = 201, response_body = ?::jsonb
                WHERE sleutel = ? AND rekeninghouder_id = ?
                """).params(json.writeValueAsString(antwoord), sleutel, rekeninghouder).update();
        return antwoord;
    }

    private static String sha256(String tekst) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tekst.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
