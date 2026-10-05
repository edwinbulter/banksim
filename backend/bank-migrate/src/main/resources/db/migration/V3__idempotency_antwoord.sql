-- Idempotency (TO §6): de sleutel wordt eerst als "in behandeling" vastgelegd, zodat een gelijktijdig
-- herhaald verzoek wacht en daarna het opgeslagen antwoord krijgt. Daarna wordt het antwoord ingevuld.
ALTER TABLE idempotency_key ALTER COLUMN response_status SET DEFAULT 0;
GRANT UPDATE (response_status, response_body) ON idempotency_key TO bank_app;
