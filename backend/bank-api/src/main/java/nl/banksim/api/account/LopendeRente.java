package nl.banksim.api.account;

import java.time.LocalDate;

import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;

/** Opgebouwde, nog niet bijgeschreven rente; geleverd door de module savings. */
public interface LopendeRente {

    Money tot(Iban spaarrekening, LocalDate datum);
}
