#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../scripts/util.sh"

java -jar validator_cli.jar -version 4.0.1 -level error \
  -output-style csv -output result.csv \
  -tx http://localhost:8080/fhir -authorise-non-conformant-tx-servers \
  -clear-tx-cache \
  -ig de.medizininformatikinitiative.kerndatensatz.person#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.laborbefund#2025.0.2 \
  -ig de.medizininformatikinitiative.kerndatensatz.medikation#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.prozedur#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.diagnose#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.fall#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.icu#2025.0.2 \
  -ig de.medizininformatikinitiative.kerndatensatz.biobank#2025.0.4 \
  -ig de.medizininformatikinitiative.kerndatensatz.consent#2025.0.1 \
  -ig de.medizininformatikinitiative.kerndatensatz.molgen#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.patho#2025.0.2 \
  -ig de.medizininformatikinitiative.kerndatensatz.mikrobiologie#2025.0.1 \
  -ig de.medizininformatikinitiative.kerndatensatz.onkologie#2025.0.3 \
  -ig de.medizininformatikinitiative.kerndatensatz.bildgebung#2025.0.0 \
  -ig de.medizininformatikinitiative.kerndatensatz.studie#2025.0.0 \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Condition-mii-exa-test-data-patient-1-diagnose-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Condition-mii-exa-test-data-patient-1-patho-problem-list-item-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Condition-mii-exa-test-data-patient-1-patho-problem-list-item-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Condition-mii-exa-test-data-patient-1-todesursache-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Condition-mii-exa-test-data-patient-2-todesursache-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Condition-mii-exa-test-data-patient-4-diagnose-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Device-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/DeviceMetric-mii-exa-test-data-patient-1-ecmo-eingestellte-parameter.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/DeviceMetric-mii-exa-test-data-patient-1-ecmo-gemessene-parameter.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/DiagnosticReport-mii-exa-test-data-patient-1-labreport-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/DiagnosticReport-mii-exa-test-data-patient-1-mibi-befund-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/DiagnosticReport-mii-exa-test-data-patient-4-molgen-befundbericht-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Encounter-mii-exa-test-data-patient-1-encounter-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Encounter-mii-exa-test-data-patient-1-encounter-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/EvidenceVariable-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Group-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/ImagingStudy-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/List-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Media-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationAdministration-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationRequest-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/MedicationStatement-*.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-labobs-6.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-diagnostic-conclusion-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-diagnostic-conclusion-3.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-diagnostic-conclusion-grouper.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-macro-grouper-a.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-macro-grouper-b.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-micro-grouper-a.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-patho-tissue-length-b.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-vitalstatus-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-2-vitalstatus-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-3-molgen-ergebnis-zusammenfassung-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-3-molgen-msi-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-3-molgen-mutationslast-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-3-patho-fnding-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Organization-mii-exa-test-data-organization-charite.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Organization-mii-exa-test-data-organization-labor-berlin.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Patient-mii-exa-test-data-patient-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Patient-mii-exa-test-data-patient-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Patient-mii-exa-test-data-patient-4.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Practitioner-mii-exa-test-data-practitioner-physician-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Practitioner-mii-exa-test-data-practitioner-physician-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/ServiceRequest-mii-exa-test-data-patient-1-labrequest-1.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/ServiceRequest-mii-exa-test-data-patient-1-labrequest-2.json" \
  "$SCRIPT_DIR/kds-testdata-2024.0.1/resources/Task-mii-exa-test-data-patient-3-molgen-medikationsempfehlung-1.json"

cat result.csv

test "#failures" "$(tail -n +2 result.csv | grep -vc Information)" "0"
