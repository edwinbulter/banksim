# BankSim – centrale ingang voor bouwen, testen en installeren.
# Installeren/verwijderen gaat via deploy/scripts (zie README.md).

MVNW      := backend/mvnw -f backend/pom.xml -B -ntp
FRONTEND  := frontend
E2E       := e2e

.PHONY: help build test backend-build backend-test frontend-build frontend-test \
        e2e-install e2e install uninstall dev-up dev-down clean

help: ## Toon beschikbare targets
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-16s %s\n", $$1, $$2}'

build: backend-build frontend-build ## Backend en frontend bouwen (zonder tests)

test: backend-test frontend-test ## Alle backend- en frontendtests draaien

backend-build: ## Backend bouwen zonder tests
	$(MVNW) package -DskipTests

backend-test: ## Backend: unit-, integratie- en architectuurtests
	$(MVNW) verify

frontend-build: ## Angular-app bouwen
	@if [ -f $(FRONTEND)/package.json ]; then npm --prefix $(FRONTEND) ci && npm --prefix $(FRONTEND) run build; else echo "frontend/ bestaat nog niet, overgeslagen"; fi

frontend-test: ## Angular unit-tests
	@if [ -f $(FRONTEND)/package.json ]; then npm --prefix $(FRONTEND) ci && npm --prefix $(FRONTEND) test; else echo "frontend/ bestaat nog niet, overgeslagen"; fi

e2e-install: ## Playwright en browsers installeren
	npm --prefix $(E2E) ci
	npm --prefix $(E2E) run install-browsers

e2e: ## Playwright e2e-tests tegen de installatie in namespace banksim (installeer met install.sh --e2e)
	npm --prefix $(E2E) ci
	npm --prefix $(E2E) test

install: ## BankSim installeren in kind-cluster single-node
	deploy/scripts/install.sh

uninstall: ## BankSim verwijderen uit kind-cluster single-node
	deploy/scripts/uninstall.sh

dev-up: ## Lokale PostgreSQL en Keycloak starten (alleen ontwikkeling)
	docker compose -f dev/compose.yaml up -d --wait

dev-down: ## Lokale ontwikkelomgeving stoppen
	docker compose -f dev/compose.yaml down

clean: ## Build-output verwijderen
	$(MVNW) clean
	rm -rf $(FRONTEND)/dist $(E2E)/test-results $(E2E)/playwright-report
