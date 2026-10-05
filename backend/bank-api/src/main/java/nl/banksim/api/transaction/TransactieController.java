package nl.banksim.api.transaction;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import nl.banksim.api.account.Rekening;
import nl.banksim.api.account.RekeningService;
import nl.banksim.api.common.Bedragen;
import nl.banksim.api.common.Fout;
import nl.banksim.api.contract.TransactiesApi;
import nl.banksim.api.contract.model.PartijDto;
import nl.banksim.api.contract.model.TransactieDto;
import nl.banksim.api.contract.model.TransactiePaginaDto;
import nl.banksim.api.contract.model.TransactieTypeDto;
import nl.banksim.api.simulation.SimulationClock;
import nl.banksim.domain.geld.Money;
import nl.banksim.domain.rekening.RekeningSoort;
import nl.banksim.domain.transactie.TransactieType;

@RestController
class TransactieController implements TransactiesApi {

    private final RekeningService rekeningen;
    private final TransactieRepository repository;
    private final SimulationClock klok;
    private final byte[] cursorSleutel;

    TransactieController(RekeningService rekeningen, TransactieRepository repository, SimulationClock klok,
                         @Value("${banksim.cursor-sleutel}") String cursorSleutel) {
        this.rekeningen = rekeningen;
        this.repository = repository;
        this.klok = klok;
        this.cursorSleutel = cursorSleutel.getBytes(StandardCharsets.UTF_8);
        if (this.cursorSleutel.length < 32) {
            throw new IllegalStateException("banksim.cursor-sleutel moet minstens 32 tekens zijn");
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('klant', 'admin')")
    public ResponseEntity<TransactiePaginaDto> getTransacties(String iban, String cursor, Integer size, String q,
                                                              String min, String max, TransactieTypeDto type,
                                                              String direction) {
        Rekening rekening = rekeningen.leesbaar(iban);
        Money van = min == null ? null : Bedragen.lees(min).abs();
        Money tot = max == null ? null : Bedragen.lees(max).abs();
        if (van != null && tot != null && van.isGreaterThan(tot)) {
            throw Fout.ongeldig("'Bedrag van' mag niet groter zijn dan 'Bedrag t/m'.");
        }
        Zoekfilter.Richting richting;
        try {
            richting = Zoekfilter.Richting.valueOf(direction == null ? "ALL" : direction);
        } catch (IllegalArgumentException e) {
            throw Fout.ongeldig("Ongeldige richting.");
        }
        Zoekfilter filter = new Zoekfilter(q, van, tot, type == null ? null : TransactieType.valueOf(type.name()), richting);
        int aantal = size == null ? 50 : size;
        Cursor na = cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor, cursorSleutel);

        LocalDate vandaag = klok.vandaag();
        List<TransactieRij> rijen = repository.zoek(rekening.iban(), rekening.soort(), vandaag, filter, na, aantal + 1);
        boolean meer = rijen.size() > aantal;
        List<TransactieRij> pagina = meer ? rijen.subList(0, aantal) : rijen;

        var antwoord = new TransactiePaginaDto(pagina.stream().map(r -> transactie(rekening, r)).toList());
        if (meer) {
            TransactieRij laatste = pagina.getLast();
            antwoord.setNextCursor(new Cursor(laatste.tijdstip(), laatste.id()).encode(cursorSleutel));
        }
        return ResponseEntity.ok(antwoord);
    }

    static TransactieDto transactie(Rekening rekening, TransactieRij rij) {
        TransactieType type = weergaveType(rekening.soort(), rij.type());
        PartijDto eigen = new PartijDto(rekening.houderNaam()).iban(rekening.iban().value());
        PartijDto tegen = new PartijDto(rij.tegenNaam()).iban(rij.tegenIban());
        boolean af = rij.bedrag().isNegative();
        var dto = new TransactieDto(rij.id(), rij.boekdatum(), rij.tijdstip().atOffset(ZoneOffset.UTC), rij.uitvoerDatum(),
                rij.tegenNaam(), rij.bedrag().toString(), TransactieTypeDto.valueOf(type.name()), type.label(),
                rekening.houderNaam(), af ? eigen : tegen, af ? tegen : eigen);
        dto.setTegenIban(rij.tegenIban());
        dto.setOmschrijving(rij.omschrijving());
        dto.setBetalingskenmerk(rij.betalingskenmerk());
        dto.setExtraOmschrijving(rij.extraOmschrijving());
        return dto;
    }

    /** Inleg en opname zijn op de betaalrekening een gewone overschrijving (TO §6). */
    static TransactieType weergaveType(RekeningSoort soort, TransactieType type) {
        if (soort == RekeningSoort.BETAAL && (type == TransactieType.INLEG || type == TransactieType.OPNAME)) {
            return TransactieType.OVERSCHRIJVING;
        }
        return type;
    }
}
