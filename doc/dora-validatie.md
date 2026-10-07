# BankSim – DORA-validatie

Gap-analyse van BankSim tegen de Digital Operational Resilience Act. Getoetst op commit `e573f62`, 7 oktober 2026.

## 1. Doel, scope en aannames

### 1.1 Bronnen

| Bron | Gebruik |
| --- | --- |
| Verordening (EU) 2022/2554 (DORA), Nederlandse tekst op EUR-Lex | Artikelen 5–45 |
| Gedelegeerde Verordening (EU) 2024/1774 (RTS ICT-risicobeheerkader) | Concrete technische eisen bij DORA art. 9–12; in dit document "RTS art. x" |

### 1.2 Aannames

- **DORA geldt voor financiële entiteiten, niet voor software.** BankSim is een simulatie op een lokaal kind-cluster. Dit document beoordeelt BankSim *alsof* het het internetbankiersysteem van een kredietinstelling is, dat een kritieke of belangrijke functie ondersteunt. Het is een gap-analyse en geen conformiteitsverklaring.
- **Geen vereenvoudigd kader.** Een kredietinstelling valt niet onder art. 16 DORA (vereenvoudigd kader). Daarom geldt het volledige kader van art. 5–15 en titel II van de RTS. Titel III van de RTS (art. 28–41) is n.v.t.
- **Alleen de repository is getoetst.** Bewijs komt uit code, configuratie, scripts, tests en CI in deze repository, met `pad:regel`. Beweringen uit het technisch ontwerp (TO) zijn alleen meegeteld als ze in de code terug te vinden zijn; waar dat niet zo is, staat het in hoofdstuk 10.
- **Veel van DORA is organisatorisch.** Governance, beleid, contracten, meldingen aan de toezichthouder, scholing en het register van informatie bestaan niet in een codebase. Die eisen staan erbij voor de volledigheid, met de status *Organisatorisch*.
- **Alleen rapporteren.** Dit document wijzigt niets; aanbevelingen staan in hoofdstuk 11.

### 1.3 Statuslegenda

| Status | Betekenis |
| --- | --- |
| **Aantoonbaar** | De eis (voor zover technisch) is in de repo aantoonbaar ingevuld |
| **Deels** | Er zijn technische maatregelen, maar een wezenlijk onderdeel ontbreekt |
| **Gap** | Technisch invulbaar, maar ontbreekt in de repo |
| **Organisatorisch** | Proces, beleid of governance; buiten de scope van de repo |
| **N.v.t.** | Niet van toepassing op BankSim of op een kredietinstelling in deze rol |

## 2. Samenvatting

BankSim is technisch sterk op **bescherming en preventie** (art. 9) en **testen** (art. 24–25, met uitzondering van performance- en pentests): zero trust met mTLS tussen alle componenten, default-deny NetworkPolicies, PostgreSQL-authenticatie met certificaten, versleutelde BFF-sessies, deny-by-default-autorisatie die door ArchUnit wordt bewaakt, en een uitgebreide testpiramide met Toxiproxy-resiliencetests, concurrency-tests, mutation testing en wekelijkse kwetsbaarheidsscans.

De grootste tekortkomingen liggen bij **herstel** en **detectie**: er is geen back-up en geen herstelprocedure, PostgreSQL en Keycloak draaien met één replica, en er is geen monitoring, alerting of centrale logopslag. Daarmee zijn art. 10, 11 en 12 en het technische deel van art. 17 niet of slechts deels ingevuld.

| Status | Aantal eisen |
| --- | --- |
| Aantoonbaar | 12 |
| Deels | 20 |
| Gap | 7 |
| Organisatorisch | 14 |
| N.v.t. | 4 |

Belangrijkste gaps (uitgewerkt in hoofdstuk 11):

| # | Gap | DORA / RTS | Prioriteit |
| --- | --- | --- | --- |
| G1 | Geen back-up, geen restore-procedure en geen restore-test van PostgreSQL; de PVC gebruikt storage class `standard` (local-path met reclaim `Delete`, TO §15.1) | DORA art. 12; RTS art. 24, 26 | Hoog |
| G2 | Geen monitoring, metrics of alerting; detectie van afwijkend gedrag ontbreekt | DORA art. 10, 17; RTS art. 23 | Hoog |
| G3 | Logging niet ingericht als beheerst proces: geen retentie, geen centrale opslag, geen gestructureerd formaat; login- en toegangsweigeringen niet in de audit log | RTS art. 12 | Hoog |
| G4 | PostgreSQL en Keycloak zijn single points of failure (1 replica, geen PDB) | DORA art. 11, 12 lid 4; RTS art. 24 | Midden |
| G5 | Geen hersteldoelstellingen (RTO/RPO) of DR-test | DORA art. 11 lid 5–6, 12 lid 6; RTS art. 24–26 | Midden |
| G6 | MFA niet geconfigureerd, ook niet voor de beheerder | RTS art. 21 f) ii) | Midden |
| G7 | ingress-nginx 1.11.3 is end-of-support en wordt in CI ongepind van GitHub geladen | DORA art. 8 lid 7, 28; RTS art. 10 | Midden |

## 3. ICT-risicobeheerkader (DORA art. 5–8)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| Art. 5 | Governance: het leidinggevend orgaan is eindverantwoordelijk voor ICT-risico | – | Organisatorisch | Buiten de repo |
| Art. 6 | Gedocumenteerd ICT-risicobeheerkader, jaarlijks geëvalueerd; RTS art. 27 verslag | `doc/technisch-ontwerp.md` (hfst. 10–12) | Deels | Het TO beschrijft maatregelen en een STRIDE-aanpak (OWASP A06), maar er is geen risicoregister of risicobeoordeling per asset |
| Art. 7 | ICT-systemen betrouwbaar, met voldoende capaciteit en weerbaar onder stress | `deploy/banksim/values.yaml:36` (replicas), `deploy/banksim/values.yaml:41` (resources), `backend/bank-api/src/main/resources/application.yaml:21` (Hikari-pool als bulkhead) | Deels | Requests en limits per pod, 2 replicas voor api, bff en web. Geen loadtest en geen capaciteitsmonitoring (RTS art. 9) |
| Art. 8 lid 1, 4, 6 | Identificatie en classificatie van ICT-assets en informatieassets; inventaris (RTS art. 4–5) | `deploy/banksim/templates/`, `backend/pom.xml:194` (CycloneDX-SBOM), `scripts/scan-images.sh` (SBOM per image) | Deels | Componenten, images en libraries zijn volledig geïnventariseerd via Helm-chart en SBOM's. Er is geen classificatie van data (bijv. "vertrouwelijk: transacties") en geen eigenaar per asset |
| Art. 8 lid 2–3 | Risico-identificatie, ook bij grote wijzigingen | `doc/technisch-ontwerp.md` (hfst. 11, A06) | Organisatorisch | Er is geen vastgelegd threat model per flow in de repo, ondanks de verwijzing in het TO |
| Art. 8 lid 7 | Jaarlijkse risicobeoordeling van legacy-ICT-systemen | `doc/technisch-ontwerp.md` (§15.1) | Deels | Het TO benoemt dat ingress-nginx end-of-support is, maar er is geen beoordeling of migratieplan (G7) |

## 4. Bescherming en voorkoming (DORA art. 9; RTS art. 6–21)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| RTS art. 6 | Encryptie van data in transit, ook interne verbindingen | `backend/bank-api/src/main/resources/application.yaml:33` (`client-auth: need`), `backend/bank-api/src/main/resources/application.yaml:19` (`sslmode=verify-full`), `deploy/banksim/templates/ingress.yaml:6` (`proxy-ssl-verify`), `deploy/banksim/files/realm-banksim.json:5` (`sslRequired: all`) | Aantoonbaar | Alle verbindingen zijn TLS met wederzijdse authenticatie |
| RTS art. 6 lid 2 a | Encryptie van data in rust | `backend/bank-bff/src/main/java/nl/banksim/bff/sessie/SessieVersleuteling.java:20` (AES-256-GCM voor tokens in de sessiestore) | Deels | Sessies zijn versleuteld. De PostgreSQL-volume (grootboek, audit log) is niet versleuteld; in een echte omgeving encryptie op volume- of storage-niveau |
| RTS art. 6 lid 1 | Gedocumenteerd cryptografiebeleid | `doc/technisch-ontwerp.md` (§10.1, A04) | Deels | Keuzes staan in het TO (RSA 3072/2048, SHA-256, AES-GCM, JWT RS256), maar niet als beleid met criteria en review bij nieuwe cryptoanalyse (lid 3–4) |
| RTS art. 7 | Sleutelbeheer over de hele levenscyclus; certificaatregister; tijdig verlengen | `deploy/scripts/certs.sh:15` (CA 825 dagen, certificaten 90 dagen), `deploy/scripts/certs.sh:72` (verlengen als < 30 dagen geldig), `deploy/scripts/install.sh:111` (willekeurige secrets) | Deels | Verlengen gebeurt alleen als iemand `install.sh` draait; er is geen bewaking van de vervaldatum (lid 5) en geen register. De CA-sleutel staat ongecodeerd in `deploy/.secrets/`; er is geen procedure voor intrekken of vervangen bij compromittering (lid 3). Overweeg cert-manager en een HSM/KMS voor de CA |
| RTS art. 8 | ICT-operaties gedocumenteerd | `deploy/scripts/install.sh`, `deploy/scripts/uninstall.sh`, `deploy/scripts/reset-data.sh`, `doc/banksim-handleiding.md` | Aantoonbaar | Installatie, upgrade en verwijdering zijn gescript, idempotent en gedocumenteerd |
| RTS art. 9 | Capaciteits- en performancemanagement | `deploy/banksim/values.yaml:41` | Gap | Wel limieten, maar geen meting, geen metrics en geen drempels. Zie G2 |
| RTS art. 10 lid 2 b | Geautomatiseerde kwetsbaarheidsscans, minimaal wekelijks voor kritieke assets | `.github/workflows/ci.yml:9` (wekelijkse run), `backend/pom.xml:241` (Dependency-Check, CVSS ≥ 7), `.github/workflows/ci.yml:77` (`npm audit`), `.github/workflows/ci.yml:177` (Trivy) | Aantoonbaar | Wekelijks en bij elke wijziging. Dependency-Check wordt overgeslagen zonder `NVD_API_KEY`; borg dat dat secret gezet is |
| RTS art. 10 lid 2 d | Gebruik van third-party libraries bijhouden | `backend/pom.xml:194`, `scripts/scan-images.sh` | Aantoonbaar | CycloneDX-SBOM's voor backend, frontend en elk image |
| RTS art. 10 lid 2 e | Responsible disclosure-procedure | – | Gap | Er is geen `SECURITY.md`. Voeg er een toe met meldadres en afhandeltermijn |
| RTS art. 10 lid 2 f–h, lid 3–4 | Patches prioriteren, testen en de afhandeling vastleggen | `renovate.json` (beveiligingsupdates direct, overige wekelijks), `backend/pom.xml:47` (Tomcat- en Jackson-overrides met CVE's), `backend/dependency-check-suppressions.xml` (onderdrukkingen met reden en einddatum) | Aantoonbaar | Patches lopen via pull requests door de volledige CI |
| RTS art. 11 | Gegevens- en systeembeveiliging, hardening | `deploy/banksim/templates/_helpers.tpl:19` en `deploy/banksim/templates/_helpers.tpl:30` (Pod Security "restricted": non-root, read-only rootfs, `drop: ALL`, seccomp; gebruikt door alle pods, ook Keycloak en PostgreSQL), `deploy/banksim/templates/_helpers.tpl:20` (geen service-account-token) | Aantoonbaar | |
| RTS art. 12 | Logging: events, retentie, bescherming, klokbron | `backend/bank-api/src/main/java/nl/banksim/api/audit/AuditLog.java:24`, `backend/bank-migrate/src/main/resources/db/migration/V2__grootboek.sql:109` (append-only), `backend/bank-migrate/src/test/java/nl/banksim/migrate/GrootboekSchemaTests.java:118`, `backend/bank-api/src/main/java/nl/banksim/api/common/CorrelationIdFilter.java:24`, `backend/bank-domain/src/main/java/nl/banksim/domain/rekening/Iban.java:67` (gemaskeerde IBAN) | Deels | Betalingen, inleg/opname en de simulatiedatum komen in een audit log waarin de applicatie niet kan wijzigen. Ontbreekt: login- en toegangsevents (lid 2 c i), retentie (lid 2 a), centrale opslag, detectie van uitval van logging (e), en een gedocumenteerde klokbron (f). `bank_datagen` mag `audit_log` legen (`backend/bank-migrate/src/main/resources/db/migration/V2__grootboek.sql:144`); dat ondermijnt append-only. Zie G3 |
| RTS art. 13 a, d, g | Netwerksegmentatie en toegangscontrole tot het netwerk | `deploy/banksim/templates/networkpolicies.yaml:22` (default-deny ingress en egress), `backend/bank-platform/src/main/java/nl/banksim/platform/mtls/MtlsClientFilter.java:41` (allow-list van CN's) | Aantoonbaar | Na installatie controleert een rooktest dat een pod zonder toestemming `bank-api` niet bereikt (`deploy/scripts/install.sh:180`) |
| RTS art. 13 b | Documentatie van netwerkverbindingen en gegevensstromen | `doc/technisch-ontwerp.md` (§10.1) | Aantoonbaar | Toegestane paden staan expliciet in het TO en komen overeen met de NetworkPolicies |
| RTS art. 13 c | Apart beheernetwerk | `backend/bank-api/src/main/resources/application.yaml:47` (management-poort 8081, alleen `health`) | Deels | De health-poorten zijn gescheiden en alleen open voor het node-IP; er is geen apart beheernetwerk (kind-beperking) |
| RTS art. 13 i | Jaarlijkse review van netwerkarchitectuur en firewallregels | – | Organisatorisch | |
| RTS art. 14 | Beveiliging van informatie in transit, datalekpreventie | Zie RTS art. 6; `backend/bank-bff/src/main/java/nl/banksim/bff/security/SecurityConfig.java` (tokens alleen server-side) | Aantoonbaar | Tokens komen nooit in de browser (BFF-patroon) |
| RTS art. 15–16 | ICT-projectmanagement; ontwikkeling, testen en goedkeuring vóór productie | `.github/workflows/ci.yml`, `doc/technisch-ontwerp.md` (hfst. 13–14) | Aantoonbaar | Zie hoofdstuk 7. Voor broncode-analyse (SAST) is alleen ESLint aanwezig; overweeg Semgrep of CodeQL |
| RTS art. 16 lid 1 c | Bescherming tegen manipulatie tijdens ontwikkeling en uitrol | `.github/workflows/ci.yml:14` (`permissions: contents: read`), `renovate.json` (Actions op commit-SHA), `deploy/banksim/values.yaml:15` (images op digest) | Deels | Images zijn niet ondertekend (geen cosign). `.github/workflows/ci.yml:171` haalt het ingress-nginx-manifest ongepind van GitHub (G7) |
| RTS art. 17 | ICT-wijzigingsbeheer: test, goedkeuring, functiescheiding, fall-back | `.github/workflows/ci.yml:8` (CI op elke pull request), `scripts/check-contract.sh` (geen brekende API-wijziging), `backend/bank-migrate/src/main/resources/db/migration/` (Flyway met checksums), `deploy/banksim/values.yaml:7` (rollback via oude ReplicaSets) | Deels | De technische poort is sterk. Functiescheiding (lid 1 b) vereist branch protection met een verplichte review; die staat niet in de repo en is niet aantoonbaar. Er is geen rollbackprocedure voor databasemigraties (Flyway kent geen down-migraties) |
| RTS art. 18–19 | Fysieke beveiliging, HR-beleid | – | Organisatorisch | |
| RTS art. 20 | Identiteitsbeheer: unieke identiteit, levenscyclus | `deploy/banksim/files/realm-banksim.json:6` (geen zelfregistratie), `backend/bank-datagen/src/main/java/nl/banksim/datagen/keycloak/KeycloakGebruikersBeheer.java` | Deels | Persoonlijke accounts per rekeninghouder; het beheer van accounts (in-, door- en uitstroom) is organisatorisch |
| RTS art. 21 a, d | Least privilege; beperking van toegang | `backend/bank-api/src/test/java/nl/banksim/api/ArchitectuurTests.java:42` (`@PreAuthorize` verplicht), `backend/bank-api/src/main/java/nl/banksim/api/account/RekeningService.java:43` (eigenaarschap), `deploy/banksim/templates/postgres.yaml:12` (DB-gebruiker per component), `backend/bank-migrate/src/main/resources/db/migration/V2__grootboek.sql:136` (alleen INSERT op het grootboek) | Aantoonbaar | Admin is alleen-lezen; een klant ziet alleen eigen rekeningen (404 op andermans IBAN) |
| RTS art. 21 f | Authenticatie, sterk voor privileged access en kritieke functies | `deploy/banksim/files/realm-banksim.json:9` (brute-force-detectie), `deploy/banksim/files/realm-banksim.json:14` (wachtwoordbeleid), `deploy/banksim/files/realm-banksim.json:15` (access token 5 min), `deploy/banksim/files/realm-banksim.json:18` (refresh-token-rotatie) | Deels | Er is geen MFA geconfigureerd, ook niet als verplichte actie voor de rol `admin` (G6). Het wachtwoordbeleid controleert niet op gelekte wachtwoorden |
| RTS art. 21 e iv | Periodieke herziening van toegangsrechten | – | Organisatorisch | |

## 5. Detectie, respons, herstel en back-up (DORA art. 10–14)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| Art. 10; RTS art. 23 | Snel afwijkende activiteiten detecteren, met drempels en meerdere controlelagen | `backend/bank-api/src/main/java/nl/banksim/api/account/RekeningService.java:43` (waarschuwing bij geweigerde toegang), `backend/bank-bff/src/main/java/nl/banksim/bff/ratelimit/RateLimitFilter.java:35` (rate limiting) | Gap | Er zijn geen metrics, dashboards of alertregels; de Micrometer-alertregel uit het TO (A09) bestaat niet. Logregels alleen in de console van de pod. Zie G2 |
| Art. 11 lid 1–3; RTS art. 24 | ICT-bedrijfscontinuïteitsbeleid, respons- en herstelplannen | `backend/bank-bff/src/main/resources/application.yaml:64` (circuit breaker), `backend/bank-bff/src/main/java/nl/banksim/bff/routing/ApiFallback.java:12`, `deploy/banksim/templates/bank-api.yaml:81` (PDB), `doc/technisch-ontwerp.md` (hfst. 12) | Deels | Technische weerbaarheid tegen uitval van API, database en Keycloak is goed ontworpen en getest. Er is geen continuïteitsplan of runbook en er zijn geen scenario's voor het verlies van het hele cluster |
| Art. 11 lid 6; RTS art. 25 | Continuïteitsplannen minstens jaarlijks testen | `backend/bank-api/src/test/java/nl/banksim/api/integratie/DatabaseUitvalTests.java:43`, `e2e/tests/storing.spec.ts:19` | Deels | Uitval van componenten wordt bij elke build getest. Er is geen test van herstel uit back-up of van uitwijk (G5) |
| Art. 11 lid 5, art. 12 lid 6; RTS art. 24 lid 1 b ii | Business impact analysis, RTO/RPO | – | Gap | Leg RTO en RPO vast voor betalen en inzien (G5) |
| Art. 12 lid 1–2 | Back-upbeleid; back-ups gescheiden van de bron | `deploy/banksim/values.yaml:65` (storage class `standard`: local-path, `Delete`), `deploy/scripts/uninstall.sh:41` (namespace en data verwijderd) | Gap | Geen `pg_dump`, geen WAL-archivering, geen back-upopslag buiten het cluster. Bij verlies van de node is alle data weg (G1) |
| Art. 12 lid 2 | Restore- en herstelprocedures, periodiek getest | `deploy/scripts/reset-data.sh` | Gap | `reset-data.sh` maakt deterministische testdata opnieuw; dat is geen herstel van productiedata. Voeg een restore-script en een periodieke restore-test toe (G1) |
| Art. 12 lid 4 | Redundante ICT-capaciteit | `deploy/banksim/values.yaml:36` (api, bff en web 2×), `deploy/banksim/templates/postgres.yaml:54` (PostgreSQL 1×), `deploy/banksim/templates/keycloak.yaml:34` (Keycloak 1×) | Deels | De stateless componenten zijn redundant. PostgreSQL en Keycloak zijn single points of failure (G4) |
| Art. 12 lid 7 | Integriteit van data na herstel controleren | `backend/bank-datagen/src/main/java/nl/banksim/datagen/database/Invarianten.java`, `backend/bank-migrate/src/test/java/nl/banksim/migrate/GrootboekSchemaTests.java` | Deels | De invarianten van het grootboek (double-entry, nooit rood) worden bij het genereren gecontroleerd. Maak ze herbruikbaar als controle na een restore |
| Art. 13 | Leren en evolueren: post-incident-reviews, scholing | – | Organisatorisch | |
| Art. 14 | Crisiscommunicatie naar klanten en publiek | `backend/bank-bff/src/main/java/nl/banksim/bff/routing/ApiFallback.java:12` (nette foutmelding met "Opnieuw proberen") | Organisatorisch | De UI geeft bij een storing een begrijpelijke melding; het communicatiebeleid zelf is organisatorisch |

## 6. ICT-incidenten (DORA art. 17–23)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| Art. 17 lid 1–3; RTS art. 22 | Incidentbeheerproces: detecteren, vastleggen, classificeren, oorzaak bepalen | `backend/bank-api/src/main/java/nl/banksim/api/common/CorrelationIdFilter.java:24` (correlation-id in MDC en antwoord), `backend/bank-api/src/main/java/nl/banksim/api/common/ProblemAdvice.java:67` | Deels | De correlation-id helpt bij het herleiden van één request. Er is geen logpatroon of gestructureerd logformaat geconfigureerd, dus de id staat niet aantoonbaar in de logregels. De BFF zet geen correlation-id. Zonder centrale logs en alerts (G2, G3) is een incident moeilijk te reconstrueren |
| Art. 18 | Classificatie van incidenten en cyberdreigingen | – | Organisatorisch | |
| Art. 19–20 | Melden van ernstige incidenten aan DNB/ECB | – | Organisatorisch | |
| Art. 21–22 | Centrale melding, feedback van toezichthouders | – | N.v.t. | Verplichtingen van de toezichthouders |
| Art. 23 | Betalingsgerelateerde incidenten (kredietinstellingen) | – | Organisatorisch | Valt onder art. 17–19 |

## 7. Testen van digitale operationele weerbaarheid (DORA art. 24–27)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| Art. 24 | Risicogebaseerd testprogramma, minstens jaarlijks voor kritieke systemen, bevindingen opgevolgd | `.github/workflows/ci.yml:26` (backend), `.github/workflows/ci.yml:51` (frontend), `.github/workflows/ci.yml:87` (contract), `.github/workflows/ci.yml:108` (dependency-check), `.github/workflows/ci.yml:141` (e2e); wekelijks via `.github/workflows/ci.yml:9` | Aantoonbaar | Het programma draait bij elke wijziging en wekelijks |
| Art. 25 | Kwetsbaarheidsscans, open-source-analyse, netwerkbeveiliging, scenario-, compatibiliteits-, performance- en end-to-end-tests, broncodereview | Kwetsbaarheden: `scripts/scan-images.sh`, `backend/pom.xml:241`. Weerbaarheid: `backend/bank-api/src/test/java/nl/banksim/api/integratie/DatabaseUitvalTests.java`, `backend/bank-api/src/test/java/nl/banksim/api/integratie/GelijktijdigheidTests.java:30`, `e2e/tests/storing.spec.ts`. Netwerk: rooktest in `deploy/scripts/install.sh`. End-to-end: `e2e/tests/`. Kwaliteit: `backend/bank-domain/pom.xml:74` (JaCoCo ≥ 85%), `backend/bank-domain/pom.xml:111` (PIT ≥ 75%) | Deels | Zeer volledig, behalve performancetests (geen loadtest) en een penetratietest. Een SAST-tool ontbreekt (zie RTS art. 16) |
| Art. 26–27 | Threat-led penetration testing (TLPT) | – | N.v.t. | Alleen voor door de toezichthouder aangewezen entiteiten; voor een simulatie niet van toepassing |

## 8. Derde aanbieders van ICT-diensten (DORA art. 28–44)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| Art. 28 lid 1–2 | Beleid voor ICT-derdenrisico | – | Organisatorisch | |
| Art. 28 lid 3 | Register van informatie over alle ICT-contracten | – | Organisatorisch | BankSim gebruikt alleen open-source-componenten zonder contract (Keycloak, PostgreSQL, Spring, Angular, ingress-nginx). De SBOM's zijn een technische basis voor het register |
| Art. 28 lid 4–5 | Risicobeoordeling vóór aanschaf; alleen dienstverleners met passende standaarden | `backend/pom.xml` (geen eigen `<repositories>`, dus alleen Maven Central; geen `.npmrc`, dus alleen npmjs), `deploy/banksim/values.yaml:15` (officiële images op digest) | Deels | De herkomst is beperkt en gepind, maar onderhoudsstatus wordt niet bewaakt: ingress-nginx is end-of-support (G7) |
| Art. 28 lid 8 | Exitstrategie voor kritieke diensten | `doc/technisch-ontwerp.md` (§15.1, Gateway API genoemd) | Gap | Er is geen exitplan voor ingress-nginx of Keycloak. Begin met ingress-nginx, omdat die geen updates meer krijgt |
| Art. 29 | Concentratierisico | – | Organisatorisch | |
| Art. 30 | Contractuele bepalingen | – | Organisatorisch | |
| Art. 31–44 | Oversight van kritieke derde aanbieders | – | N.v.t. | Verplichtingen van de toezichthouders |

## 9. Informatie-uitwisseling (DORA art. 45)

| Artikel | Eis | Bewijs | Status | Bevinding / aanbeveling |
| --- | --- | --- | --- | --- |
| Art. 45 | Vrijwillige uitwisseling van informatie over cyberdreigingen | – | N.v.t. | Vrijwillig; organisatorisch als de instelling eraan deelneemt |

## 10. Afwijkingen tussen technisch ontwerp en code

Bij de toetsing bleek een aantal beweringen in het TO niet (geheel) in de code te staan. Ze tellen niet als bewijs en verklaren een deel van de gaps.

| TO | Bewering | Bevinding in de code |
| --- | --- | --- |
| §11 A09 | `audit_log` voor login-events (via Keycloak-events) en geweigerde toegang | De realm zet geen events aan (`deploy/banksim/files/realm-banksim.json`); geweigerde toegang staat alleen als `log.warn` in `backend/bank-api/src/main/java/nl/banksim/api/account/RekeningService.java:43`, niet in `audit_log` |
| §11 A09 | Gestructureerde JSON-logs met correlation-id | Geen `logging.structured`- of logpatroonconfiguratie in de `application.yaml`'s; Spring Boot logt dan als platte tekst zonder de MDC-waarde |
| §11 A09 | Micrometer-metrics op mislukte logins en 403/404-pieken met een alertregel | Geen Micrometer-registry, metrics-endpoint (`management.endpoints.web.exposure.include: health`) of alertregel |
| §11 A10 | Error Prone / Sonar tegen lege `catch`-blokken | Niet in `backend/pom.xml` of in CI |
| §11 A08 | Audit log append-only | Voor de applicatie wel (`backend/bank-migrate/src/test/java/nl/banksim/migrate/GrootboekSchemaTests.java:118`), maar `bank_datagen` heeft `TRUNCATE` (`backend/bank-migrate/src/main/resources/db/migration/V2__grootboek.sql:144`) |

## 11. Aanbevelingen

| # | Actie | Waar in de repo | Prioriteit |
| --- | --- | --- | --- |
| G1 | CronJob met `pg_dump` (of WAL-archivering) naar opslag buiten het cluster; `restore.sh`; restore-test in CI die na herstel de grootboekinvarianten controleert | `deploy/banksim/templates/`, `deploy/scripts/`, `.github/workflows/ci.yml` | Hoog |
| G2 | Micrometer met Prometheus-endpoint op de management-poort; alertregels op 5xx, 401/403/404-pieken, mislukte logins, open circuit breaker, verlopende certificaten | `backend/*/pom.xml`, `application.yaml`, `deploy/banksim/templates/` | Hoog |
| G3 | Gestructureerde JSON-logs (`logging.structured.format.console: ecs`) met correlation-id ook in de BFF; Keycloak-events aanzetten; geweigerde toegang in `audit_log`; retentie vastleggen; `TRUNCATE` op `audit_log` intrekken voor `bank_datagen` | `application.yaml`'s, `realm-banksim.json`, nieuwe Flyway-migratie | Hoog |
| G4 | PostgreSQL met replicatie (bijv. CloudNativePG) en Keycloak met 2 replicas en een gedeelde cache; PDB's | `deploy/banksim/templates/postgres.yaml`, `deploy/banksim/templates/keycloak.yaml` | Midden |
| G5 | Continuïteitsparagraaf in het TO met RTO/RPO per functie, uitvalscenario's en een runbook; jaarlijkse DR-test | `doc/` | Midden |
| G6 | TOTP verplicht voor de rol `admin` (required action of conditional OTP-flow) en optioneel voor klanten; wachtwoordbeleid uitbreiden | `deploy/banksim/files/realm-banksim.json` | Midden |
| G7 | Exitplan van ingress-nginx naar een Gateway API-implementatie; tot dan het CI-manifest pinnen op checksum | `doc/technisch-ontwerp.md`, `.github/workflows/ci.yml:171` | Midden |
| – | Certificaten automatisch verlengen en de vervaldatum bewaken (cert-manager); CA-sleutel buiten de werkplek | `deploy/scripts/certs.sh` | Midden |
| – | `SECURITY.md` met responsible-disclosure-procedure | repo-root | Laag |
| – | SAST (CodeQL of Semgrep) en een loadtest in CI | `.github/workflows/ci.yml` | Laag |
| – | Images ondertekenen met cosign en de handtekening controleren bij installatie | `scripts/`, `deploy/scripts/install.sh` | Laag |
| – | Branch protection met verplichte review vastleggen in de README (functiescheiding bij wijzigingen) | `README.md` | Laag |
| – | Dataclassificatie en een risicoregister per asset als bijlage bij het TO | `doc/` | Laag |
