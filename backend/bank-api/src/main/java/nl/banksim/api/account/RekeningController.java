package nl.banksim.api.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import nl.banksim.api.contract.MeApi;
import nl.banksim.api.contract.RekeningenApi;
import nl.banksim.api.contract.model.GebruikerDto;
import nl.banksim.api.contract.model.RekeningDetailDto;
import nl.banksim.api.contract.model.RekeningOverzichtDto;
import nl.banksim.api.contract.model.RekeningSamenvattingDto;
import nl.banksim.api.contract.model.RekeningSoortDto;
import nl.banksim.api.security.IngelogdeGebruiker;
import nl.banksim.api.simulation.SimulationClock;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.rente.RenteCalculator;

@RestController
class RekeningController implements MeApi, RekeningenApi {

    private final RekeningService rekeningen;
    private final IngelogdeGebruiker gebruiker;
    private final SimulationClock klok;
    private final LopendeRente lopendeRente;

    RekeningController(RekeningService rekeningen, IngelogdeGebruiker gebruiker, SimulationClock klok,
                       LopendeRente lopendeRente) {
        this.rekeningen = rekeningen;
        this.gebruiker = gebruiker;
        this.klok = klok;
        this.lopendeRente = lopendeRente;
    }

    @Override
    @PreAuthorize("hasAnyRole('klant', 'admin')")
    public ResponseEntity<GebruikerDto> getMe() {
        return ResponseEntity.ok(new GebruikerDto(gebruiker.naam(), gebruiker.gebruikersnaam(), gebruiker.rollen()));
    }

    @Override
    @PreAuthorize("hasRole('klant')")
    public ResponseEntity<RekeningOverzichtDto> getMijnRekeningen() {
        return ResponseEntity.ok(overzicht(rekeningen.mijnRekeningen(), klok.vandaag(), rekeningen));
    }

    @Override
    @PreAuthorize("hasAnyRole('klant', 'admin')")
    public ResponseEntity<RekeningDetailDto> getRekening(String iban) {
        Rekening rekening = rekeningen.leesbaar(iban);
        LocalDate vandaag = klok.vandaag();
        var detail = new RekeningDetailDto(rekening.iban().value(), soort(rekening.soort()), rekening.houderNaam(),
                rekeningen.saldo(rekening.iban(), vandaag).toString(), vandaag, !gebruiker.isKlant());
        rekeningen.gekoppeld(rekening).ifPresent(g -> detail.setGekoppeldeRekening(g.value()));
        if (rekening.soort() == RekeningSoort.SPAAR) {
            detail.setLopendeRente(lopendeRente.tot(rekening.iban(), vandaag).toString());
            detail.setRentepercentage(RenteCalculator.STANDAARD_PERCENTAGE.multiply(BigDecimal.valueOf(100))
                    .setScale(2).toPlainString());
        }
        return ResponseEntity.ok(detail);
    }

    static RekeningOverzichtDto overzicht(List<Rekening> lijst, LocalDate datum, RekeningService rekeningen) {
        var overzicht = new RekeningOverzichtDto(datum, lijst.stream()
                .filter(r -> r.soort() != RekeningSoort.EXTERN)
                .map(r -> new RekeningSamenvattingDto(r.iban().value(), soort(r.soort()),
                        rekeningen.saldo(r.iban(), datum).toString()))
                .toList());
        lijst.stream().findFirst().ifPresent(r -> overzicht.setRekeninghouder(r.houderNaam()));
        return overzicht;
    }

    static RekeningSoortDto soort(RekeningSoort soort) {
        return RekeningSoortDto.valueOf(soort.name());
    }
}
