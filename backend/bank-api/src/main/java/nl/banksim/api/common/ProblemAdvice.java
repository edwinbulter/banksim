package nl.banksim.api.common;

import java.net.URI;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import nl.banksim.domain.grootboek.SaldoOntoereikendException;

/**
 * Elke fout wordt een ProblemDetail (RFC 9457) zonder interne details of stacktraces (TO §11, A10). Uitval
 * van de database wordt 503 met Retry-After, zodat de client het later opnieuw kan proberen (TO §12).
 */
@RestControllerAdvice
class ProblemAdvice {

    static final String TYPE_BASIS = "https://banksim.local/problems/";
    private static final Logger log = LoggerFactory.getLogger(ProblemAdvice.class);

    @ExceptionHandler(Fout.class)
    ResponseEntity<ProblemDetail> fout(Fout fout) {
        return antwoord(fout.status(), fout.code(), fout.titel(), fout.getMessage());
    }

    @ExceptionHandler(SaldoOntoereikendException.class)
    ResponseEntity<ProblemDetail> saldoOntoereikend(SaldoOntoereikendException e) {
        log.info("Boeking geweigerd: saldo ontoereikend op {}", e.rekening().gemaskeerd());
        return antwoord(HttpStatus.UNPROCESSABLE_CONTENT, "saldo-ontoereikend", "Saldo ontoereikend",
                "Deze boeking zou het saldo onder € 0,00 brengen, nu of op een latere datum.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> ongeldig(Exception e) {
        String velden = e instanceof MethodArgumentNotValidException m
                ? m.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).distinct()
                        .collect(Collectors.joining(", "))
                : "";
        String detail = velden.isEmpty() ? "Het verzoek bevat ongeldige of ontbrekende gegevens."
                : "Ongeldige of ontbrekende velden: " + velden + ".";
        return antwoord(HttpStatus.BAD_REQUEST, "ongeldig-verzoek", "Ongeldig verzoek", detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> geenToegang(AccessDeniedException e) {
        return antwoord(HttpStatus.FORBIDDEN, "geen-toegang", "Geen toegang", "Je hebt geen toegang tot deze functie.");
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, CannotCreateTransactionException.class,
            QueryTimeoutException.class, TransactionTimedOutException.class, TransientDataAccessException.class,
            DataAccessResourceFailureException.class})
    ResponseEntity<ProblemDetail> tijdelijkNietBeschikbaar(Exception e) {
        log.warn("Database tijdelijk niet beschikbaar: {}", e.getClass().getSimpleName());
        ResponseEntity<ProblemDetail> antwoord = antwoord(HttpStatus.SERVICE_UNAVAILABLE, "tijdelijk-niet-beschikbaar",
                "Tijdelijk niet beschikbaar", "De bank is even niet bereikbaar. Probeer het over een paar seconden opnieuw.");
        return ResponseEntity.status(antwoord.getStatusCode()).header(HttpHeaders.RETRY_AFTER, "5")
                .body(antwoord.getBody());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> onverwacht(Exception e) {
        log.error("Onverwachte fout", e);
        return antwoord(HttpStatus.INTERNAL_SERVER_ERROR, "interne-fout", "Er ging iets mis",
                "Er ging iets mis. Probeer het later opnieuw.");
    }

    private static ResponseEntity<ProblemDetail> antwoord(HttpStatus status, String code, String titel, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASIS + code));
        problem.setTitle(titel);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
