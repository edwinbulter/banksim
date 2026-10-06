package nl.banksim.api.common;

import org.springframework.http.HttpStatus;

/**
 * Een verwachte fout die als ProblemDetail (RFC 9457) naar de client gaat.
 *
 * @see ProblemAdvice
 */
public class Fout extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String titel;

    public Fout(HttpStatus status, String code, String titel, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.titel = titel;
    }

    /** 404 – ook voor rekeningen van anderen, zodat niet uitlekt dat ze bestaan (TO §10.3). */
    public static Fout nietGevonden(String wat) {
        return new Fout(HttpStatus.NOT_FOUND, "niet-gevonden", "Niet gevonden", wat + " is niet gevonden.");
    }

    public static Fout ongeldig(String detail) {
        return new Fout(HttpStatus.BAD_REQUEST, "ongeldig-verzoek", "Ongeldig verzoek", detail);
    }

    /** 422 – het verzoek is correct opgebouwd maar mag volgens de bedrijfsregels niet. */
    public static Fout bedrijfsregel(String code, String titel, String detail) {
        return new Fout(HttpStatus.UNPROCESSABLE_CONTENT, code, titel, detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String titel() {
        return titel;
    }
}
