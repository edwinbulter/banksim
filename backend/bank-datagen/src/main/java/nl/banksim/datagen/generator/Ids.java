package nl.banksim.datagen.generator;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Deterministische UUID's: dezelfde seed geeft altijd dezelfde id's. */
public final class Ids {

    private Ids() {
    }

    public static UUID uuid(String sleutel) {
        return UUID.nameUUIDFromBytes(("banksim:" + sleutel).getBytes(StandardCharsets.UTF_8));
    }
}
