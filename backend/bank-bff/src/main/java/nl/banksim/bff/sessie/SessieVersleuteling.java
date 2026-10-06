package nl.banksim.bff.sessie;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Versleutelt sessie-attributen (access-, refresh- en ID-token, beveiligingscontext) met AES-256-GCM voordat
 * Spring Session ze in PostgreSQL opslaat (TO §2, §11 A04). GCM authenticeert de inhoud: alleen bytes die de
 * BFF zelf heeft versleuteld worden ooit gedeserialiseerd (A08).
 *
 * <p>Formaat: versie (1 byte) · IV (12 bytes) · ciphertext met tag.
 */
public class SessieVersleuteling {

    private static final Logger log = LoggerFactory.getLogger(SessieVersleuteling.class);
    private static final byte VERSIE = 1;
    private static final int IV_LENGTE = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey sleutel;
    private final SecureRandom random = new SecureRandom();
    private final SerializingConverter serialiseer = new SerializingConverter();
    private final DeserializingConverter deserialiseer;

    public SessieVersleuteling(String geheim, ClassLoader classLoader) {
        if (geheim == null || geheim.length() < 32) {
            throw new IllegalStateException("banksim.bff.sessie.sleutel moet minstens 32 tekens zijn");
        }
        this.sleutel = new SecretKeySpec(sha256(geheim), "AES");
        this.deserialiseer = new DeserializingConverter(classLoader);
    }

    public byte[] versleutel(Object attribuut) {
        byte[] klaar = serialiseer.convert(attribuut);
        try {
            byte[] iv = new byte[IV_LENGTE];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, sleutel, new GCMParameterSpec(TAG_BITS, iv));
            byte[] versleuteld = cipher.doFinal(klaar);
            return ByteBuffer.allocate(1 + IV_LENGTE + versleuteld.length).put(VERSIE).put(iv).put(versleuteld).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Versleutelen van sessie-attribuut mislukt", e);
        }
    }

    /**
     * @return het attribuut, of {@code null} als het niet te ontsleutelen is (andere sleutel, gemanipuleerd);
     *         de gebruiker moet dan opnieuw inloggen
     */
    public Object ontsleutel(byte[] opgeslagen) {
        if (opgeslagen == null || opgeslagen.length <= 1 + IV_LENGTE || opgeslagen[0] != VERSIE) {
            log.warn("Sessie-attribuut heeft een onbekend formaat en wordt genegeerd");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, sleutel, new GCMParameterSpec(TAG_BITS, opgeslagen, 1, IV_LENGTE));
            byte[] klaar = cipher.doFinal(opgeslagen, 1 + IV_LENGTE, opgeslagen.length - 1 - IV_LENGTE);
            return deserialiseer.convert(klaar);
        } catch (GeneralSecurityException e) {
            log.warn("Sessie-attribuut kon niet worden ontsleuteld en wordt genegeerd");
            return null;
        }
    }

    private static byte[] sha256(String geheim) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(geheim.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
