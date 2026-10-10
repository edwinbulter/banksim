# DORA en NIS2

Twee Europese wetten gaan over de digitale weerbaarheid van organisaties: DORA en NIS2. Voor een bank, en dus voor BankSim als we het behandelen alsof het een echte bank is (zie [dora-validatie.md](dora-validatie.md)), is DORA de wet die telt. Dit document legt uit waarom.

## 1. Wat betekent DORA?

**DORA** staat voor **Digital Operational Resilience Act**. De officiële Nederlandse naam is *Verordening (EU) 2022/2554 betreffende digitale operationele weerbaarheid voor de financiële sector*.

- In werking getreden op 16 januari 2023, van toepassing sinds **17 januari 2025**.
- Geldt voor ruim twintig soorten financiële entiteiten (art. 2), zoals banken, betalings- en elektronischgeldinstellingen, beleggingsondernemingen, verzekeraars, pensioenfondsen en cryptodienstverleners. Ook kritieke ICT-dienstverleners van de financiële sector vallen eronder, bijvoorbeeld grote cloudaanbieders.
- In Nederland houden **DNB** en de **AFM** toezicht. Op kritieke ICT-dienstverleners houden de Europese toezichthouders (EBA, EIOPA, ESMA) rechtstreeks toezicht.

**NIS2** is *Richtlijn (EU) 2022/2555 betreffende maatregelen voor een hoge gemeenschappelijke cyberbeveiliging in de Unie*. NIS staat voor *Network and Information Security*; de 2 geeft aan dat het de opvolger is van de eerste NIS-richtlijn uit 2016. Nederland heeft NIS2 omgezet in de **Cyberbeveiligingswet (Cbw)**, die op 15 augustus 2026 in werking is getreden.

## 2. Verordening tegenover richtlijn

Het belangrijkste verschil zit in het soort wet (art. 288 VWEU):

| | DORA (verordening) | NIS2 (richtlijn) |
| --- | --- | --- |
| Werking | Rechtstreeks van toepassing in alle lidstaten, zonder omzetting | Bindt lidstaten alleen aan het **resultaat**; elke lidstaat kiest zelf vorm en middelen |
| Wie is gebonden | De financiële entiteit zelf, rechtstreeks | De lidstaat; organisaties pas via de nationale wet (in Nederland de Cbw) |
| Uniformiteit | Overal dezelfde tekst en dezelfde eisen | Minimumharmonisatie (art. 5): lidstaten mogen strengere of extra eisen stellen, dus verschillen per land |
| Detailniveau | Zeer gedetailleerd, aangevuld met technische standaarden (RTS/ITS) die ook verordeningen zijn | Kader met doelen; invulling in nationale wet- en regelgeving |
| Werkingssfeer | Alleen de financiële sector | 18 sectoren, waaronder energie, vervoer, zorg, drinkwater, digitale infrastructuur en ook bankwezen en financiëlemarktinfrastructuur |

"Minder dwingend" betekent hier dus vooral: **minder rechtstreeks en minder uniform**. Een richtlijn is net zo bindend, maar voor de lidstaat, en het resultaat hangt af van de nationale wet. Voor een organisatie die onder de Cbw valt, zijn de verplichtingen even hard: de Cbw kent zorgplicht, meldplicht, registratieplicht en boetes. NIS2 schrijft voor dat de maximale boete voor essentiële entiteiten minstens € 10 miljoen of 2% van de wereldwijde jaaromzet is (art. 34).

## 3. Hoe verhouden ze zich?

DORA is de **sectorspecifieke uitwerking (lex specialis)** van NIS2 voor de financiële sector:

- **Art. 4 NIS2**: als een sectorspecifieke EU-wet minstens gelijkwaardige eisen stelt aan risicobeheer of incidentmelding, gelden die eisen in plaats van de NIS2-eisen.
- **Art. 1 lid 2 DORA**: DORA geldt voor de toepassing van art. 4 NIS2 als zo'n sectorspecifieke wet.
- De Cbw neemt dit over: de zorgplicht van de Cbw geldt niet als een EU-sectorwet gelijkwaardige verplichtingen oplegt.
- De Rijksoverheid vat het samen als: waar DORA en NIS2 overlappen, gaan voor financiële instellingen de regels van DORA voor.

Vereenvoudigd: **DORA bevat voor de financiële sector de kern van NIS2 en gaat op een aantal punten verder.** Twee nuances:

1. DORA bevat niet *alles* van NIS2. NIS2 gaat ook over de 17 andere sectoren, over nationale cyberstrategieën, CSIRT's (in Nederland het NCSC) en samenwerking tussen lidstaten. Die onderdelen staan niet in DORA.
2. De voorrang geldt alleen voor de onderwerpen die DORA gelijkwaardig regelt (vooral risicobeheer en incidentmelding). DORA zorgt wel dat ernstige incidenten ook bij de NIS2-autoriteiten terechtkomen (art. 19 DORA).

Waar DORA verder gaat dan NIS2:

| Onderwerp | NIS2 | DORA |
| --- | --- | --- |
| Risicobeheer | Tien minimummaatregelen (art. 21) | Volledig ICT-risicobeheerkader (art. 5–16) plus een gedetailleerde RTS (Gedelegeerde Verordening (EU) 2024/1774) |
| Incidentmelding | Vroegtijdige waarschuwing binnen 24 uur, melding binnen 72 uur, eindverslag binnen 1 maand | Vergelijkbare termijnen, maar met verplichte classificatiecriteria en vaste meldformulieren |
| Testen | Niet specifiek voorgeschreven | Jaarlijks testprogramma en, voor significante instellingen, elke 3 jaar een threat-led penetration test (TLPT) |
| Derde partijen | Aandacht voor de toeleveringsketen | Register van alle ICT-contracten, verplichte contractbepalingen, exitstrategieën en direct EU-toezicht op kritieke ICT-dienstverleners |

## 4. De regels van DORA

DORA bestaat uit vijf pijlers.

### 4.1 ICT-risicobeheer (art. 5–16)

- Het **bestuur** is eindverantwoordelijk voor het ICT-risicobeheer en moet er zelf voldoende kennis van hebben (art. 5).
- Een gedocumenteerd **ICT-risicobeheerkader**, minstens jaarlijks herzien (art. 6).
- ICT-middelen en -afhankelijkheden in kaart brengen (art. 8), beschermen en voorkomen (art. 9), afwijkingen detecteren (art. 10).
- **Bedrijfscontinuïteit, back-up en herstel**: beleid, plannen en geteste herstelprocedures (art. 11–12).
- Leren en communiceren na incidenten (art. 13–14).
- Kleine, minder complexe instellingen mogen een vereenvoudigd kader gebruiken (art. 16).

### 4.2 ICT-incidenten beheren, classificeren en melden (art. 17–23)

- Een proces om incidenten te detecteren, te registreren en af te handelen (art. 17).
- Incidenten classificeren volgens vaste criteria, zoals aantal getroffen klanten, duur, geografische spreiding, gegevensverlies en economische impact (art. 18).
- **Ernstige ICT-incidenten melden** bij de toezichthouder (DNB of AFM): een eerste melding, een tussentijds verslag en een eindverslag (art. 19). Significante cyberdreigingen mogen vrijwillig worden gemeld.
- Klanten informeren als een incident hun financiële belangen raakt (art. 19 lid 3).

### 4.3 Testen van digitale operationele weerbaarheid (art. 24–27)

- Een **testprogramma** met onder meer kwetsbaarheidsscans, broncode-analyse, scenario-, prestatie- en penetratietests (art. 24–25). Kritieke systemen worden minstens jaarlijks getest.
- Significante instellingen laten minstens elke 3 jaar een **TLPT** uitvoeren: een realistische aanval op de productieomgeving (art. 26–27). In Nederland gebeurt dat via het TIBER-NL-programma van DNB.

### 4.4 Beheer van ICT-risico's van derde partijen (art. 28–44)

- Een **informatieregister** met alle contracten met ICT-dienstverleners (art. 28 lid 3).
- Risico's beoordelen vóór het sluiten van een contract, en concentratierisico vermijden (art. 28–29).
- Verplichte **contractbepalingen**, zoals dienstniveaus, auditrechten, locatie van gegevens en exitregelingen (art. 30).
- Een **toezichtskader** voor kritieke ICT-dienstverleners, met een Europese hoofdtoezichthouder die onder meer dwangsommen kan opleggen (art. 31–44).

### 4.5 Informatie delen (art. 45)

- Financiële instellingen mogen onderling informatie over cyberdreigingen delen binnen vertrouwde gemeenschappen.

### 4.6 Handhaving

DORA laat de sancties voor financiële instellingen aan de lidstaten over (art. 50). In Nederland zijn de bevoegdheden van DNB en de AFM vastgelegd in de Wet op het financieel toezicht.

## 5. Officiële bronnen

### DORA

| Bron | Link |
| --- | --- |
| Verordening (EU) 2022/2554, Nederlandse tekst (EUR-Lex) | <https://eur-lex.europa.eu/legal-content/NL/TXT/?uri=CELEX:32022R2554> |
| Rijksoverheid – DORA | <https://www.rijksoverheid.nl/themas/economie/financiele-sector/dora> |
| DNB – DORA: het toezicht van DNB per 17 januari 2025 | <https://www.dnb.nl/nieuws-voor-de-sector/toezicht-2024/dora-het-toezicht-van-dnb-per-17-januari-2025/> |
| AFM – DORA | <https://www.afm.nl/nl-nl/sector/themas/digitalisering/dora> |

### NIS2 en Cyberbeveiligingswet

| Bron | Link |
| --- | --- |
| Richtlijn (EU) 2022/2555, Nederlandse tekst (EUR-Lex) | <https://eur-lex.europa.eu/legal-content/NL/TXT/?uri=CELEX:32022L2555> |
| Cyberbeveiligingswet (wetten.overheid.nl) | <https://wetten.overheid.nl/BWBR0052872/2026-08-15/0> |
| NCSC – Cyberbeveiligingswet (NIS2) | <https://www.ncsc.nl/cyberbeveiligingswet-nis2> |
| NCTV – Cyberbeveiligingswet | <https://www.nctv.nl/onderwerpen/c/cyberbeveiligingswet> |
