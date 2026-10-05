-- Sessies van bank-bff (Spring Session JDBC), gedeeld over alle BFF-replicas (TO §2).
-- Schema en tabellen zijn van bank_migrate; bank_bff krijgt alleen DML-rechten op dit schema.

CREATE SCHEMA bff;

CREATE TABLE bff.spring_session (
    primary_id            CHAR(36)     NOT NULL,
    session_id            CHAR(36)     NOT NULL,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON bff.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON bff.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON bff.spring_session (principal_name);

CREATE TABLE bff.spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES bff.spring_session (primary_id) ON DELETE CASCADE
);

GRANT USAGE ON SCHEMA bff TO bank_bff;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA bff TO bank_bff;
