package nl.banksim.domain.rekening;

/** Soort rekening in het grootboek (TO §4). */
public enum RekeningSoort {

    /** Betaalrekening van een huishouden. */
    BETAAL,
    /** Spaarrekening van een huishouden. */
    SPAAR,
    /** Rekening van een bedrijf of van BankSim zelf (rente, kas); mag negatief worden. */
    EXTERN;

    /** Betaal- en spaarrekeningen mogen nooit rood staan (FO). */
    public boolean magNietRoodStaan() {
        return this != EXTERN;
    }
}
