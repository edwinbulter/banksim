-- Rate limiting van bank-bff (TO §10.1): Bucket4j bewaart de buckets in PostgreSQL, zodat de limiet geldt
-- over alle BFF-replica's samen. Verlopen buckets worden door de BFF opgeruimd.
CREATE TABLE bff.bucket (
    id         VARCHAR(160) PRIMARY KEY,
    state      BYTEA,
    expires_at BIGINT
);

CREATE INDEX bucket_expires_at_ix ON bff.bucket (expires_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON bff.bucket TO bank_bff;
