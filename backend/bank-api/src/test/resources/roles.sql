-- Zelfde rollen als het PostgreSQL-init-script in de Helm-chart; in tests met wachtwoord in plaats van certificaat.
CREATE ROLE bank_migrate LOGIN;
CREATE ROLE bank_app LOGIN PASSWORD 'bank_app';
CREATE ROLE bank_bff LOGIN;
CREATE ROLE bank_datagen LOGIN;
