-- Grootboek van BankSim (TO §4). Elke IBAN is een rekening; elke geldbeweging is een overboeking met precies
-- twee boekingen die samen nul zijn. Saldo's worden berekend, nooit opgeslagen.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE rekeninghouder (
    id           UUID        PRIMARY KEY,
    naam         VARCHAR(70) NOT NULL,
    soort        VARCHAR(16) NOT NULL CHECK (soort IN ('HUISHOUDEN', 'BEDRIJF', 'BANK')),
    -- Koppeling met Keycloak; alleen huishoudens kunnen inloggen.
    keycloak_sub VARCHAR(64) UNIQUE,
    CHECK (keycloak_sub IS NULL OR soort = 'HUISHOUDEN')
);

CREATE TABLE rekening (
    iban              VARCHAR(34)    PRIMARY KEY,
    rekeninghouder_id UUID           NOT NULL REFERENCES rekeninghouder (id),
    soort             VARCHAR(8)     NOT NULL CHECK (soort IN ('BETAAL', 'SPAAR', 'EXTERN')),
    openingssaldo     NUMERIC(19, 2) NOT NULL DEFAULT 0,
    geopend_op        DATE           NOT NULL,
    -- Alleen gebruikt om de rij te vergrendelen (SELECT … FOR UPDATE) en te versioneren.
    versie            BIGINT         NOT NULL DEFAULT 0,
    CHECK (iban ~ '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$')
);

CREATE INDEX rekening_houder_ix ON rekening (rekeninghouder_id);

-- Rekeningen waarnaar betaald mag worden (FO: alleen bestaande rekeningen uit de fake data).
CREATE TABLE contact (
    iban      VARCHAR(34) PRIMARY KEY REFERENCES rekening (iban),
    naam      VARCHAR(70) NOT NULL,
    categorie VARCHAR(32) NOT NULL
);

CREATE INDEX contact_naam_trgm ON contact USING gin (naam gin_trgm_ops);

CREATE TABLE overboeking (
    id                  UUID         PRIMARY KEY,
    type                VARCHAR(24)  NOT NULL CHECK (type IN ('ONLINE_BANKIEREN', 'IDEAL_WERO', 'BETAALAUTOMAAT',
                            'INCASSO', 'GELDAUTOMAAT', 'OVERSCHRIJVING', 'VERZAMELBETALING', 'INLEG', 'OPNAME', 'RENTE')),
    transactie_tijdstip TIMESTAMPTZ  NOT NULL,
    uitvoer_datum       DATE         NOT NULL,
    omschrijving        VARCHAR(140),
    betalingskenmerk    VARCHAR(25),
    extra_omschrijving  VARCHAR(35)
);

CREATE INDEX overboeking_omschrijving_trgm ON overboeking USING gin (omschrijving gin_trgm_ops);

CREATE TABLE boeking (
    id                  UUID           PRIMARY KEY,
    overboeking_id      UUID           NOT NULL REFERENCES overboeking (id),
    rekening_iban       VARCHAR(34)    NOT NULL REFERENCES rekening (iban),
    tegen_iban          VARCHAR(34),
    tegen_naam          VARCHAR(70)    NOT NULL,
    bedrag              NUMERIC(19, 2) NOT NULL CHECK (bedrag <> 0),
    boekdatum           DATE           NOT NULL,
    -- Kopie uit overboeking, zodat de transactielijst zonder join op (tijdstip, id) gepagineerd kan worden.
    transactie_tijdstip TIMESTAMPTZ    NOT NULL
);

-- Saldo op datum en keyset-paginering van de transactielijst ("Toon meer").
CREATE INDEX boeking_rekening_lijst_ix ON boeking (rekening_iban, transactie_tijdstip DESC, id DESC);
CREATE INDEX boeking_rekening_datum_ix ON boeking (rekening_iban, boekdatum) INCLUDE (bedrag);
CREATE INDEX boeking_overboeking_ix ON boeking (overboeking_id);
CREATE INDEX boeking_tegen_naam_trgm ON boeking USING gin (tegen_naam gin_trgm_ops);

-- Laatste verdedigingslinie (TO §4/§6): bij commit moet elke geraakte overboeking uit precies twee boekingen
-- op twee verschillende rekeningen bestaan die samen nul zijn.
CREATE FUNCTION controleer_overboeking_in_balans() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    v_overboeking UUID := COALESCE(NEW.overboeking_id, OLD.overboeking_id);
    v_aantal      INT;
    v_rekeningen  INT;
    v_som         NUMERIC;
BEGIN
    SELECT count(*), count(DISTINCT rekening_iban), COALESCE(sum(bedrag), 0)
    INTO v_aantal, v_rekeningen, v_som
    FROM boeking
    WHERE overboeking_id = v_overboeking;

    IF v_aantal <> 2 OR v_rekeningen <> 2 OR v_som <> 0 THEN
        RAISE EXCEPTION 'Overboeking % is niet in balans: % boekingen op % rekeningen, som %',
            v_overboeking, v_aantal, v_rekeningen, v_som
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER boeking_in_balans
    AFTER INSERT OR UPDATE OR DELETE ON boeking
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION controleer_overboeking_in_balans();

-- Herhaalde betaalverzoeken met dezelfde sleutel leveren één overboeking op (TO §6).
CREATE TABLE idempotency_key (
    sleutel           UUID        NOT NULL,
    rekeninghouder_id UUID        NOT NULL REFERENCES rekeninghouder (id),
    request_hash      CHAR(64)    NOT NULL,
    response_status   SMALLINT    NOT NULL,
    response_body     JSONB,
    aangemaakt_op     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (sleutel, rekeninghouder_id)
);

-- Append-only: de applicatie mag alleen toevoegen (TO §11, A09).
CREATE TABLE audit_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tijdstip       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor          VARCHAR(64) NOT NULL,
    actie          VARCHAR(64) NOT NULL,
    details        JSONB,
    correlation_id VARCHAR(64)
);

CREATE INDEX audit_log_tijdstip_ix ON audit_log (tijdstip);

-- Eén rij met de instellingen van de simulatie (TO §8).
CREATE TABLE instelling (
    id             SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    simulatiedatum DATE,
    data_vanaf     DATE NOT NULL,
    data_tot       DATE NOT NULL,
    CHECK (data_vanaf <= data_tot),
    CHECK (simulatiedatum IS NULL OR simulatiedatum BETWEEN data_vanaf AND data_tot)
);

INSERT INTO instelling (id, simulatiedatum, data_vanaf, data_tot)
VALUES (1, NULL, DATE '2021-10-01', DATE '2026-12-31');

-- Rechten per databasegebruiker (TO §10.1): zo weinig mogelijk.
GRANT SELECT ON rekeninghouder, rekening, contact, overboeking, boeking, instelling TO bank_app;
GRANT INSERT ON overboeking, boeking, audit_log TO bank_app;
-- FOR UPDATE vereist een UPDATE-recht; alleen op de versiekolom.
GRANT UPDATE (versie) ON rekening TO bank_app;
GRANT UPDATE (simulatiedatum) ON instelling TO bank_app;
GRANT SELECT, INSERT, DELETE ON idempotency_key TO bank_app;

GRANT SELECT, INSERT, TRUNCATE ON rekeninghouder, rekening, contact, overboeking, boeking TO bank_datagen;
GRANT SELECT, UPDATE ON instelling TO bank_datagen;
GRANT TRUNCATE ON idempotency_key, audit_log TO bank_datagen;
GRANT INSERT ON audit_log TO bank_datagen;
