package nl.banksim.api.contact;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import nl.banksim.api.contract.ContactenApi;
import nl.banksim.api.contract.model.ContactDto;
import nl.banksim.api.security.IngelogdeGebruiker;

@RestController
class ContactController implements ContactenApi {

    private final ContactService contacten;
    private final IngelogdeGebruiker gebruiker;

    ContactController(ContactService contacten, IngelogdeGebruiker gebruiker) {
        this.contacten = contacten;
        this.gebruiker = gebruiker;
    }

    @Override
    @PreAuthorize("hasRole('klant')")
    public ResponseEntity<List<ContactDto>> getContacten(String q) {
        return ResponseEntity.ok(contacten.zoek(q, gebruiker.sub()).stream()
                .map(c -> new ContactDto(c.iban().value(), c.naam()))
                .toList());
    }
}
