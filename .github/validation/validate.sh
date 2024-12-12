#!/bin/bash -e

java -jar validator_cli.jar -version 4.0.1 -level error \
  -tx http://localhost:8080/fhir \
  -clear-tx-cache \
  -ig de.medizininformatikinitiative.kerndatensatz.person#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.icu#2025.0.0-ballot.1 \
  -ig de.einwilligungsmanagement#1.0.1 \
  kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-ecmo-a*.json
