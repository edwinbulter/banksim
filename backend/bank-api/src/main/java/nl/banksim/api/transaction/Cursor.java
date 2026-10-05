package nl.banksim.api.transaction;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import nl.banksim.api.common.Fout;

/**
 * Positie in de transactielijst voor keyset-paginering ("Toon meer"): het laatste (tijdstip, id). Ondertekend
 * met HMAC-SHA256, zodat een client de cursor niet kan manipuleren (TO §3.2).
 */
record Cursor(Instant tijdstip, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode(byte[] sleutel) {
        String inhoud = ENCODER.encodeToString((tijdstip + "|" + id).getBytes(StandardCharsets.UTF_8));
        return inhoud + "." + ENCODER.encodeToString(hmac(sleutel, inhoud));
    }

    static Cursor decode(String cursor, byte[] sleutel) {
        try {
            int punt = cursor.indexOf('.');
            String inhoud = cursor.substring(0, punt);
            byte[] handtekening = DECODER.decode(cursor.substring(punt + 1));
            if (!MessageDigest.isEqual(handtekening, hmac(sleutel, inhoud))) {
                throw Fout.ongeldig("Ongeldige cursor.");
            }
            String[] delen = new String(DECODER.decode(inhoud), StandardCharsets.UTF_8).split("\\|");
            return new Cursor(Instant.parse(delen[0]), UUID.fromString(delen[1]));
        } catch (RuntimeException e) {
            if (e instanceof Fout fout) {
                throw fout;
            }
            throw Fout.ongeldig("Ongeldige cursor.");
        }
    }

    private static byte[] hmac(byte[] sleutel, String inhoud) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sleutel, "HmacSHA256"));
            return mac.doFinal(inhoud.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
