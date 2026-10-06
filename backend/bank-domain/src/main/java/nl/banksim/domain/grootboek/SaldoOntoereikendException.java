package nl.banksim.domain.grootboek;

import nl.banksim.domain.rekening.Iban;

/** Een boeking zou een betaal- of spaarrekening (nu of later) onder nul brengen. */
public class SaldoOntoereikendException extends RuntimeException {

    private final transient Iban rekening;

    public SaldoOntoereikendException(Iban rekening) {
        super("Saldo ontoereikend op " + rekening.formatted());
        this.rekening = rekening;
    }

    public Iban rekening() {
        return rekening;
    }
}
