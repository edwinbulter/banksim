package nl.banksim.api.payment;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nl.banksim.api.account.Rekening;
import nl.banksim.api.account.RekeningService;
import nl.banksim.api.audit.AuditLog;
import nl.banksim.api.common.Bedragen;
import nl.banksim.api.common.Fout;
import nl.banksim.api.contact.ContactService;
import nl.banksim.api.contract.model.BetaalOpdrachtDto;
import nl.banksim.api.contract.model.BoekingsbevestigingDto;
import nl.banksim.api.contract.model.OverschrijfOpdrachtDto;
import nl.banksim.api.ledger.LedgerService;
import nl.banksim.api.security.IngelogdeGebruiker;
import nl.banksim.api.simulation.SimulationClock;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.transactie.Overboeking;
import nl.banksim.domain.transactie.Partij;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Betalen en overschrijven (FO). Alle bedrijfsregels gelden server-side, ongeacht wat de frontend toont
 * (TO §11, A06): alleen vanaf de eigen rekening, alleen naar een bekend contact met de juiste naam, nooit
 * rood, en precies één keer per Idempotency-Key.
 */
@Service
class BetaalService {

    private final RekeningService rekeningen;
    private final ContactService contacten;
    private final LedgerService ledger;
    private final Idempotency idempotency;
    private final SimulationClock klok;
    private final AuditLog audit;
    private final IngelogdeGebruiker gebruiker;

    BetaalService(RekeningService rekeningen, ContactService contacten, LedgerService ledger, Idempotency idempotency,
                  SimulationClock klok, AuditLog audit, IngelogdeGebruiker gebruiker) {
        this.rekeningen = rekeningen;
        this.contacten = contacten;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.klok = klok;
        this.audit = audit;
        this.gebruiker = gebruiker;
    }

    @Transactional(timeout = 10)
    BoekingsbevestigingDto betaal(UUID sleutel, BetaalOpdrachtDto opdracht) {
        Rekening van = rekeningen.eigen(opdracht.getVanIban());
        if (van.soort() != RekeningSoort.BETAAL) {
            throw Fout.bedrijfsregel("alleen-van-betaalrekening", "Niet toegestaan",
                    "Betalen kan alleen vanaf een betaalrekening.");
        }
        Money bedrag = positief(opdracht.getBedrag());
        if (!Iban.isGeldig(opdracht.getIbanOntvanger())) {
            throw Fout.bedrijfsregel("ongeldig-iban", "Ongeldig IBAN", "Het rekeningnummer van de ontvanger is ongeldig.");
        }
        Iban naar = Iban.of(opdracht.getIbanOntvanger());
        ContactService.Contact contact = contacten.vind(naar).orElseThrow(() -> Fout.bedrijfsregel("onbekende-ontvanger",
                "Onbekende ontvanger", "Naar dit rekeningnummer kan niet worden betaald."));
        if (!contact.naamPast(opdracht.getNaamOntvanger())) {
            throw Fout.bedrijfsregel("naam-past-niet", "Naam past niet bij rekening",
                    "De naam van de ontvanger hoort niet bij dit rekeningnummer.");
        }
        if (naar.equals(van.iban())) {
            throw Fout.bedrijfsregel("zelfde-rekening", "Niet toegestaan", "Je kunt niet naar dezelfde rekening betalen.");
        }
        String verzoek = String.join("|", "betaal", van.iban().value(), bedrag.toString(), naar.value(),
                contact.naam(), nietNull(opdracht.getOmschrijving()), nietNull(opdracht.getBetalingskenmerk()),
                nietNull(opdracht.getExtraOmschrijving()));

        return idempotency.eenmalig(sleutel, van.houderId(), verzoek, BoekingsbevestigingDto.class, () -> {
            Overboeking overboeking = new Overboeking(UUID.randomUUID(), TransactieType.ONLINE_BANKIEREN,
                    new Partij(van.iban(), van.houderNaam()), new Partij(naar, contact.naam()), bedrag, klok.nu(),
                    klok.vandaag(), opdracht.getOmschrijving(), opdracht.getBetalingskenmerk(),
                    opdracht.getExtraOmschrijving());
            ledger.boek(overboeking);
            audit.vastleggen(gebruiker.gebruikersnaam(), "BETALING", Map.of(
                    "overboeking", overboeking.id(), "van", van.iban().gemaskeerd(), "naar", naar.gemaskeerd(),
                    "bedrag", bedrag.toString()));
            return bevestiging(overboeking, van.iban());
        });
    }

    @Transactional(timeout = 10)
    BoekingsbevestigingDto schrijfOver(UUID sleutel, OverschrijfOpdrachtDto opdracht) {
        Rekening van = rekeningen.eigen(opdracht.getVanIban());
        Rekening naar = rekeningen.eigen(opdracht.getNaarIban());
        boolean inleg = van.soort() == RekeningSoort.BETAAL && naar.soort() == RekeningSoort.SPAAR;
        boolean opname = van.soort() == RekeningSoort.SPAAR && naar.soort() == RekeningSoort.BETAAL;
        if (!inleg && !opname) {
            throw Fout.bedrijfsregel("alleen-betaal-en-spaar", "Niet toegestaan",
                    "Overschrijven kan alleen tussen je eigen betaal- en spaarrekening.");
        }
        Money bedrag = positief(opdracht.getBedrag());
        String omschrijving = opdracht.getOmschrijving() == null || opdracht.getOmschrijving().isBlank()
                ? (inleg ? "Inleg" : "Opname") : opdracht.getOmschrijving();
        String verzoek = String.join("|", "overschrijf", van.iban().value(), naar.iban().value(), bedrag.toString(),
                omschrijving);

        return idempotency.eenmalig(sleutel, van.houderId(), verzoek, BoekingsbevestigingDto.class, () -> {
            Overboeking overboeking = new Overboeking(UUID.randomUUID(), inleg ? TransactieType.INLEG : TransactieType.OPNAME,
                    new Partij(van.iban(), van.houderNaam()), new Partij(naar.iban(), naar.houderNaam()), bedrag,
                    klok.nu(), klok.vandaag(), omschrijving, null, null);
            ledger.boek(overboeking);
            audit.vastleggen(gebruiker.gebruikersnaam(), inleg ? "INLEG" : "OPNAME", Map.of(
                    "overboeking", overboeking.id(), "bedrag", bedrag.toString()));
            return bevestiging(overboeking, van.iban());
        });
    }

    private BoekingsbevestigingDto bevestiging(Overboeking overboeking, Iban van) {
        LocalDate datum = overboeking.uitvoerDatum();
        return new BoekingsbevestigingDto(overboeking.id(), datum, overboeking.bedrag().toString(),
                rekeningen.saldo(van, datum).toString());
    }

    private static Money positief(String tekst) {
        Money bedrag = Bedragen.lees(tekst);
        if (!bedrag.isPositive()) {
            throw Fout.ongeldig("Het bedrag moet groter zijn dan € 0,00.");
        }
        return bedrag;
    }

    private static String nietNull(String tekst) {
        return tekst == null ? "" : tekst;
    }
}
