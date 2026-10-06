package nl.banksim.api.account;

/** Maakt zoektekst veilig voor {@code ILIKE … ESCAPE '\'}: jokertekens worden letterlijk. */
public final class Zoektekst {

    private Zoektekst() {
    }

    public static String escape(String tekst) {
        return tekst.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
