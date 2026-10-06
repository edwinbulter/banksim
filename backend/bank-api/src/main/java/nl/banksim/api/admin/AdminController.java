package nl.banksim.api.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import nl.banksim.api.account.Huishouden;
import nl.banksim.api.account.Rekening;
import nl.banksim.api.account.RekeningService;
import nl.banksim.api.audit.AuditLog;
import nl.banksim.api.common.Fout;
import nl.banksim.api.contract.AdminApi;
import nl.banksim.api.contract.model.RekeningOverzichtDto;
import nl.banksim.api.contract.model.RekeningSamenvattingDto;
import nl.banksim.api.contract.model.RekeningSoortDto;
import nl.banksim.api.contract.model.RekeninghouderSamenvattingDto;
import nl.banksim.api.contract.model.SimulatiedatumDto;
import nl.banksim.api.contract.model.SimulatiedatumWijzigingDto;
import nl.banksim.api.security.IngelogdeGebruiker;
import nl.banksim.api.simulation.SimulationClock;
import nl.banksim.domain.rekening.RekeningSoort;

/** Beheerdersfuncties (FO §Admin): simulatiedatum en alleen-lezen inzage in huishoudens. */
@RestController
@PreAuthorize("hasRole('admin')")
class AdminController implements AdminApi {

    private final SimulationClock klok;
    private final RekeningService rekeningen;
    private final AuditLog audit;
    private final IngelogdeGebruiker gebruiker;

    AdminController(SimulationClock klok, RekeningService rekeningen, AuditLog audit, IngelogdeGebruiker gebruiker) {
        this.klok = klok;
        this.rekeningen = rekeningen;
        this.audit = audit;
        this.gebruiker = gebruiker;
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<SimulatiedatumDto> getSimulatiedatum() {
        return ResponseEntity.ok(simulatiedatum());
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<SimulatiedatumDto> zetSimulatiedatum(SimulatiedatumWijzigingDto wijziging) {
        LocalDate datum = wijziging.getDatum();
        SimulationClock.Instelling instelling = klok.instelling();
        if (datum != null && (datum.isBefore(instelling.vanaf()) || datum.isAfter(instelling.tot()))) {
            throw Fout.ongeldig("De simulatiedatum moet tussen " + instelling.vanaf() + " en " + instelling.tot() + " liggen.");
        }
        klok.zet(datum);
        audit.vastleggen(gebruiker.gebruikersnaam(), "SIMULATIEDATUM_GEWIJZIGD",
                Map.of("datum", datum == null ? "systeemdatum" : datum.toString()));
        return ResponseEntity.ok(simulatiedatum());
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<List<RekeninghouderSamenvattingDto>> getRekeninghouders(String q) {
        LocalDate vandaag = klok.vandaag();
        return ResponseEntity.ok(rekeningen.huishoudens(q).stream().map(h -> samenvatting(h, vandaag)).toList());
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<RekeningOverzichtDto> getRekeningenVanRekeninghouder(UUID id) {
        LocalDate vandaag = klok.vandaag();
        List<Rekening> lijst = rekeningen.rekeningenVanHuishouden(id);
        var overzicht = new RekeningOverzichtDto(vandaag, lijst.stream()
                .filter(r -> r.soort() != RekeningSoort.EXTERN)
                .map(r -> new RekeningSamenvattingDto(r.iban().value(), RekeningSoortDto.valueOf(r.soort().name()),
                        rekeningen.saldo(r.iban(), vandaag).toString()))
                .toList());
        lijst.stream().findFirst().ifPresent(r -> overzicht.setRekeninghouder(r.houderNaam()));
        return ResponseEntity.ok(overzicht);
    }

    private RekeninghouderSamenvattingDto samenvatting(Huishouden h, LocalDate vandaag) {
        var dto = new RekeninghouderSamenvattingDto(h.id(), h.naam());
        if (h.betaal() != null) {
            dto.setBetaalrekening(h.betaal().value());
            dto.setSaldoBetaal(rekeningen.saldo(h.betaal(), vandaag).toString());
        }
        if (h.spaar() != null) {
            dto.setSpaarrekening(h.spaar().value());
            dto.setSaldoSpaar(rekeningen.saldo(h.spaar(), vandaag).toString());
        }
        return dto;
    }

    private SimulatiedatumDto simulatiedatum() {
        SimulationClock.Instelling instelling = klok.instelling();
        return new SimulatiedatumDto(klok.vandaag(), instelling.vanaf(), instelling.tot(), instelling.ingesteld() != null);
    }
}
