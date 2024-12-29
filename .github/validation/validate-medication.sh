#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

# Doesn't need Snomed CT
java -jar validator_cli.jar -version 4.0.1 -level error \
  -tx http://localhost:8080/fhir \
  -clear-tx-cache \
  -ig de.medizininformatikinitiative.kerndatensatz.medikation#2025.0.0 \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationRequest-mii-exa-test-data-patient-3-medrequest-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationRequest-mii-exa-test-data-patient-1-medrequest-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationRequest-mii-exa-test-data-patient-1-medrequest-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationStatement-mii-exa-test-data-patient-3-medstatement-1.json"
