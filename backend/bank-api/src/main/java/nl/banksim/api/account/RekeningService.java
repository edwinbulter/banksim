package nl.banksim.api.account;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import nl.banksim.api.common.Bedragen;
import nl.banksim.api.common.Fout;
import nl.banksim.api.security.IngelogdeGebruiker;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;

/**
 * Toegang tot rekeningen (TO §10.3): een klant ziet alleen zijn eigen rekeningen, de beheerder alle
 * rekeningen van huishoudens (alleen-lezen), nooit de grootboekrekeningen van bedrijven of BankSim.
 * Een rekening zonder toegang geeft 404, zodat niet uitlekt dat hij bestaat.
 */
@Service
public class RekeningService {

    private static final Logger log = LoggerFactory.getLogger(RekeningService.class);

    private final RekeningRepository repository;
    private final IngelogdeGebruiker gebruiker;

    RekeningService(RekeningRepository repository, IngelogdeGebruiker gebruiker) {
        this.repository = repository;
        this.gebruiker = gebruiker;
    }

    /** Lezen: eigen rekening (klant) of rekening van een huishouden (beheerder). */
    public Rekening leesbaar(String iban) {
        Rekening rekening = repository.vind(Bedragen.rekening(iban)).orElseThrow(() -> Fout.nietGevonden("Rekening"));
        boolean toegestaan = (gebruiker.isKlant() && gebruiker.sub().equals(rekening.houderSub()))
                || (gebruiker.isAdmin() && rekening.isVanHuishouden() && rekening.soort() != RekeningSoort.EXTERN);
        if (!toegestaan) {
            log.warn("Geen leestoegang voor {} tot {}", gebruiker.gebruikersnaam(), rekening.iban().gemaskeerd());
            throw Fout.nietGevonden("Rekening");
        }
        return rekening;
    }

    /** Schrijven (betalen, overschrijven): alleen de klant zelf, nooit de beheerder. */
    public Rekening eigen(String iban) {
        Rekening rekening = repository.vind(Bedragen.rekening(iban)).orElseThrow(() -> Fout.nietGevonden("Rekening"));
        if (!gebruiker.isKlant() || !gebruiker.sub().equals(rekening.houderSub())) {
            log.warn("Geen schrijftoegang voor {} tot {}", gebruiker.gebruikersnaam(), rekening.iban().gemaskeerd());
            throw Fout.nietGevonden("Rekening");
        }
        return rekening;
    }

    public Optional<Rekening> vind(Iban iban) {
        return repository.vind(iban);
    }

    public List<Rekening> mijnRekeningen() {
        UUID houder = repository.houderMetSub(gebruiker.sub()).orElseThrow(() -> Fout.nietGevonden("Rekeninghouder"));
        return repository.vanHouder(houder);
    }

    /** Beheerder: rekeningen van een huishouden. */
    public List<Rekening> rekeningenVanHuishouden(UUID houderId) {
        repository.huishoudenNaam(houderId).orElseThrow(() -> Fout.nietGevonden("Rekeninghouder"));
        return repository.vanHouder(houderId);
    }

    public List<Huishouden> huishoudens(String zoek) {
        return repository.huishoudens(zoek);
    }

    public Money saldo(Iban iban, LocalDate datum) {
        return repository.saldo(iban, datum);
    }

    /** De andere klantrekening van dezelfde houder (betaal ↔ spaar). */
    public Optional<Iban> gekoppeld(Rekening rekening) {
        return repository.vanHouder(rekening.houderId()).stream()
                .filter(r -> r.soort() != rekening.soort() && r.soort() != RekeningSoort.EXTERN)
                .map(Rekening::iban)
                .findFirst();
    }
}
