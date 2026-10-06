package nl.banksim.domain.transactie;

/** Transactietypen uit het FO: zeven voor de betaalrekening, drie voor de spaarrekening. */
public enum TransactieType {

    ONLINE_BANKIEREN("Online bankieren"),
    IDEAL_WERO("iDEAL | Wero"),
    BETAALAUTOMAAT("Betaalautomaat"),
    INCASSO("Incasso"),
    GELDAUTOMAAT("Geldautomaat"),
    OVERSCHRIJVING("Overschrijving"),
    VERZAMELBETALING("Verzamelbetaling"),
    INLEG("Inleg"),
    OPNAME("Opname"),
    RENTE("Rente");

    private final String label;

    TransactieType(String label) {
        this.label = label;
    }

    /** Tekst zoals het FO die toont, bijvoorbeeld "iDEAL | Wero". */
    public String label() {
        return label;
    }

    /** Typen die de klant in het zoekformulier van de betaalrekening kan kiezen. */
    public boolean isBetaalrekeningType() {
        return ordinal() <= VERZAMELBETALING.ordinal();
    }
}
