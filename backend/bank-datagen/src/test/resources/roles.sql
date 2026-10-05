-- Zelfde rollen als het PostgreSQL-init-script in de Helm-chart.
CREATE ROLE bank_migrate LOGIN;
CREATE ROLE bank_app LOGIN;
CREATE ROLE bank_bff LOGIN;
CREATE ROLE bank_datagen LOGIN;
