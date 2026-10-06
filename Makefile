# BankSim – centrale ingang voor bouwen, testen en installeren.
# Installeren/verwijderen gaat via deploy/scripts (zie README.md).

MVNW      := backend/mvnw -f backend/pom.xml -B -ntp
FRONTEND  := frontend
E2E       := e2e

.PHONY: help build test backend-build backend-test frontend-build frontend-test \
        e2e-install e2e check audit sbom dependency-check scan install uninstall dev-up dev-down clean

help: ## Toon beschikbare targets
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-16s %s\n", $$1, $$2}'

build: backend-build frontend-build ## Backend en frontend bouwen (zonder tests)

test: backend-test frontend-test ## Alle backend- en frontendtests draaien

backend-build: ## Backend bouwen zonder tests
	$(MVNW) package -DskipTests

backend-test: ## Backend: unit-, integratie- en architectuurtests
	$(MVNW) verify

frontend-build: ## Angular-app bouwen
	npm --prefix $(FRONTEND) ci
	npm --prefix $(FRONTEND) run build

frontend-test: ## Angular lint en unit-tests
	npm --prefix $(FRONTEND) ci
	npm --prefix $(FRONTEND) run lint
	npm --prefix $(FRONTEND) test

e2e-install: ## Playwright en browsers installeren
	npm --prefix $(E2E) ci
	npm --prefix $(E2E) run install-browsers

e2e: ## Playwright e2e-tests tegen de installatie in namespace banksim (installeer met install.sh --e2e)
	npm --prefix $(E2E) ci
	npm --prefix $(E2E) test

check: ## Contract achterwaarts compatibel met origin/main en gegenereerde API-client actueel
	scripts/check-contract.sh
	scripts/check-api-client.sh

audit: ## npm audit van frontend en e2e (faalt vanaf high)
	npm --prefix $(FRONTEND) audit --audit-level=high
	npm --prefix $(E2E) audit --audit-level=high

sbom: ## CycloneDX-SBOM's: backend/target/bom.json en frontend/bom.json
	$(MVNW) package -DskipTests
	cd $(FRONTEND) && npx --yes @cyclonedx/cyclonedx-npm@6.0.1 --omit dev --output-format JSON --output-file bom.json

dependency-check: ## OWASP Dependency-Check van de backend (zet NVD_API_KEY; faalt vanaf CVSS 7)
	$(MVNW) -Psecurity -DskipTests -Djacoco.skip=true -Dpit.skip=true verify

scan: ## Trivy-scan en SBOM per image van de geïnstalleerde release
	scripts/scan-images.sh "$$(helm --kube-context $${BANKSIM_CONTEXT:-kind-single-node} -n banksim get values banksim -o json | jq -r .imageTag)"

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
	rm -rf $(FRONTEND)/dist $(FRONTEND)/bom.json $(E2E)/test-results $(E2E)/playwright-report target
