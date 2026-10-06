package nl.banksim.api.audit;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Append-only audit log in de database (TO §11, A09). Nooit tokens, wachtwoorden of volledige IBAN's. */
@Component
public class AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void vastleggen(String actor, String actie, Map<String, ?> details) {
        jdbc.sql("INSERT INTO audit_log (actor, actie, details, correlation_id) VALUES (?, ?, ?::jsonb, ?)")
                .params(actor, actie, json.writeValueAsString(details), MDC.get("correlationId"))
                .update();
    }
}
