package nl.banksim.datagen.generator;

import nl.banksim.domain.geld.Money;

/**
 * Financieel profiel van een huishouden, in prijzen van 2021 per maand. De generator past inflatie en
 * seizoenen toe.
 *
 * @param inkomenBron      werkgever (werknemer) of pensioenfonds (gepensioneerd); leeg voor een zzp'er
 * @param netto            netto salaris, pensioen of beoogde omzet (zzp) per maand
 * @param aow              AOW per maand (alleen gepensioneerd)
 * @param woonlastenPartij verhuurder of hypotheekbank; leeg als het huis is afbetaald
 * @param kinderopvang     maandbedrag, of leeg
 * @param boodschappen     supermarktbezoeken per maand
 * @param web              webwinkelaankopen per maand
 * @param pinnen           geldopnames per maand
 * @param tikkies          betalingen aan andere huishoudens per maand
 */
public record Profiel(int nummer, String voornaam, String achternaam, String gebruikersnaam, String stad,
                      Type type, Bedrijf inkomenBron, Money netto, Money aow, boolean dertiendeMaand,
                      Bedrijf woonlastenPartij, Money woonlasten, int personen, int kinderen, Money energie,
                      Money kinderopvang, int boodschappen, int web, int pinnen, int tikkies, Money sparen,
                      Money startBetaal, Money startSpaar, boolean auto, boolean sport, boolean streaming) {

    public enum Type { WERKNEMER, ZZP, GEPENSIONEERD }

    public String naam() {
        return voornaam + " " + achternaam;
    }

    public int volwassenen() {
        return personen - kinderen;
    }
}
