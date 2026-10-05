package nl.banksim.datagen.generator;

import java.util.ArrayList;
import java.util.List;

import nl.banksim.domain.rekening.Iban;

/**
 * Alle fictieve tegenpartijen. Namen zijn verzonnen; elke partij heeft een geldig IBAN met bankcode SIMB.
 * Volgorde niet wijzigen: de IBAN's zijn afgeleid van de positie.
 */
public final class Bedrijven {

    public static final String BANKCODE = "SIMB";

    private static final List<Bedrijf> ALLE = new ArrayList<>();

    // Werkgevers en inkomensbronnen
    public static final Bedrijf TECHNIEK_NOORD = add("techniek-noord", "Techniek Noord B.V.", "werkgever");
    public static final Bedrijf ZORGGROEP = add("zorggroep", "Zorggroep Rivierenland", "werkgever");
    public static final Bedrijf GEMEENTE_ZUIDSTAD_WERK = add("gemeente-werk", "Gemeente Zuidstad Salarisadministratie", "werkgever");
    public static final Bedrijf LOGISTIEK = add("logistiek", "Logistiek Centraal B.V.", "werkgever");
    public static final Bedrijf ONDERWIJS = add("onderwijs", "Onderwijsgroep Midden", "werkgever");
    public static final Bedrijf BOUW = add("bouw", "Bouwbedrijf Van Dijk B.V.", "werkgever");
    public static final Bedrijf ADVIES = add("advies", "Kompas Advies B.V.", "werkgever");
    public static final Bedrijf RIJKSUITKERINGEN = add("rijksuitkeringen", "Rijksuitkeringen", "uitkering");
    public static final Bedrijf PENSIOENFONDS = add("pensioenfonds", "Pensioenfonds Midden", "uitkering");
    public static final Bedrijf KLANT_ALFA = add("klant-alfa", "Alfa Media B.V.", "opdrachtgever");
    public static final Bedrijf KLANT_BETA = add("klant-beta", "Beta Webdiensten", "opdrachtgever");
    public static final Bedrijf KLANT_GAMMA = add("klant-gamma", "Gamma Retail B.V.", "opdrachtgever");
    public static final Bedrijf BELASTINGDIENST = add("belastingdienst", "Belastingdienst", "overheid");

    // Wonen
    public static final Bedrijf WOONSTICHTING = add("woonstichting", "Woonstichting Thuis", "wonen");
    public static final Bedrijf WONINGCORPORATIE = add("woningcorporatie", "Woningcorporatie Samen", "wonen");
    public static final Bedrijf HYPOTHEEKBANK = add("hypotheekbank", "Hypotheekbank Hollandia", "wonen");
    public static final Bedrijf GEMEENTE_ZUIDSTAD = add("gemeente", "Gemeente Zuidstad", "overheid");

    // Vaste lasten
    public static final Bedrijf ENERGIE = add("energie", "Groenstroom Energie", "energie");
    public static final Bedrijf WATER = add("water", "Waterbedrijf Midden", "energie");
    public static final Bedrijf INTERNET = add("internet", "NetPlus Internet", "telecom");
    public static final Bedrijf MOBIEL = add("mobiel", "Mobiel Direct", "telecom");
    public static final Bedrijf ZORGVERZEKERAAR = add("zorgverzekeraar", "Zorgverzekeraar Gezond", "verzekering");
    public static final Bedrijf VERZEKERAAR = add("verzekeraar", "Verzekeringen Zeker", "verzekering");
    public static final Bedrijf KINDEROPVANG = add("kinderopvang", "Kinderopvang Zonnetje", "kinderopvang");
    public static final Bedrijf SPORTSCHOOL = add("sportschool", "Sportschool Fit", "vrije tijd");
    public static final Bedrijf STREAMING = add("streaming", "StreamFlix", "vrije tijd");

    // Winkels (betaalautomaat) en webwinkels (iDEAL | Wero)
    public static final Bedrijf BUURTSUPER = add("buurtsuper", "BuurtSuper", "supermarkt");
    public static final Bedrijf PRIJSVAST = add("prijsvast", "Prijsvast Supermarkt", "supermarkt");
    public static final Bedrijf VERSMARKT = add("versmarkt", "Versmarkt", "supermarkt");
    public static final Bedrijf BAKKERIJ = add("bakkerij", "Bakkerij Brood & Zo", "winkel");
    public static final Bedrijf TANKSTATION = add("tankstation", "Tankstation De Pomp", "vervoer");
    public static final Bedrijf DROGISTERIJ = add("drogisterij", "Drogisterij Gezond & Wel", "winkel");
    public static final Bedrijf WEBWINKEL = add("webwinkel", "Webwinkel Alles", "webwinkel");
    public static final Bedrijf BOEKENBOX = add("boekenbox", "BoekenBox", "webwinkel");
    public static final Bedrijf MODE = add("mode", "ModeOnline", "webwinkel");
    public static final Bedrijf ELEKTRO = add("elektro", "ElektroShop", "webwinkel");
    public static final Bedrijf REISBUREAU = add("reisbureau", "Reisbureau Zon", "reizen");
    public static final Bedrijf EETCAFE = add("eetcafe", "Eetcafé De Hoek", "horeca");
    public static final Bedrijf RESTAURANT = add("restaurant", "Restaurant Smaakvol", "horeca");
    public static final Bedrijf GARAGE = add("garage", "Garage Snelservice", "vervoer");
    public static final Bedrijf VAKANTIEPARK = add("vakantiepark", "Vakantiepark Zonneveld", "reizen");
    public static final Bedrijf MODEHUIS = add("modehuis", "Modehuis Centrum", "winkel");
    public static final Bedrijf BOUWMARKT = add("bouwmarkt", "Bouwmarkt Klusgoed", "winkel");
    public static final Bedrijf WOONWARENHUIS = add("woonwarenhuis", "Woonwarenhuis Thuis & Co", "winkel");
    public static final Bedrijf SPEELGOED = add("speelgoed", "Speelgoedwinkel Hoera", "winkel");

    // Interne rekeningen van BankSim
    public static final Bedrijf BANKSIM_RENTE = add("banksim-rente", "BankSim Rente", "bank");
    public static final Bedrijf BANKSIM_KAS = add("banksim-kas", "BankSim Geldautomaten", "bank");

    private Bedrijven() {
    }

    private static Bedrijf add(String sleutel, String naam, String categorie) {
        Bedrijf bedrijf = new Bedrijf(sleutel, naam, categorie, Iban.nl(BANKCODE, 9_000_000_000L + ALLE.size() + 1));
        ALLE.add(bedrijf);
        return bedrijf;
    }

    public static List<Bedrijf> alle() {
        return List.copyOf(ALLE);
    }

    /** Interne rekeningen zijn geen contact: daar kunnen klanten niet naartoe betalen. */
    public static boolean isIntern(Bedrijf bedrijf) {
        return "bank".equals(bedrijf.categorie());
    }
}
