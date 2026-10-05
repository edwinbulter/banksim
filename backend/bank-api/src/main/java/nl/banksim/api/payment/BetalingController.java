package nl.banksim.api.payment;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import nl.banksim.api.contract.BetalingenApi;
import nl.banksim.api.contract.model.BetaalOpdrachtDto;
import nl.banksim.api.contract.model.BoekingsbevestigingDto;
import nl.banksim.api.contract.model.OverschrijfOpdrachtDto;

/** Alleen klanten mogen boeken; de beheerder heeft alleen-lezen toegang (FO). */
@RestController
class BetalingController implements BetalingenApi {

    private final BetaalService service;

    BetalingController(BetaalService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('klant')")
    public ResponseEntity<BoekingsbevestigingDto> betaal(UUID idempotencyKey, BetaalOpdrachtDto opdracht) {
        BoekingsbevestigingDto bevestiging = service.betaal(idempotencyKey, opdracht);
        return ResponseEntity.created(URI.create("/api/payments/" + bevestiging.getOverboekingId())).body(bevestiging);
    }

    @Override
    @PreAuthorize("hasRole('klant')")
    public ResponseEntity<BoekingsbevestigingDto> schrijfOver(UUID idempotencyKey, OverschrijfOpdrachtDto opdracht) {
        BoekingsbevestigingDto bevestiging = service.schrijfOver(idempotencyKey, opdracht);
        return ResponseEntity.created(URI.create("/api/transfers/" + bevestiging.getOverboekingId())).body(bevestiging);
    }
}
