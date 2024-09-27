#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="X26238298X"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')

echo "calling \$purge with GET isn't possible"
test "GET response code" "$(curl -s -o /dev/null -w '%{response_code}' "$BASE/Patient/$PATIENT_ID/\$purge")" "405"

OUTCOME="$(curl -s -XPOST "$BASE/Patient/$PATIENT_ID/\$purge")"

test "outcome code" "$(echo "$OUTCOME" | jq -r '.issue[0].code')" "success"
test "read patient response code" "$(curl -s -o /dev/null -w '%{response_code}' "$BASE/Patient/$PATIENT_ID")" "404"

for TYPE in "CarePlan" \
  "CareTeam" \
  "Claim" \
  "Condition" \
  "DiagnosticReport" \
  "DocumentReference" \
  "Encounter" \
  "ExplanationOfBenefit" \
  "ImagingStudy" \
  "Immunization" \
  "MedicationAdministration" \
  "MedicationRequest" \
  "Observation" \
  "Procedure" \
  "Provenance"; do
  test "number of references from ${TYPE}s to the purged patient" "$(blazectl --server "$BASE" download "$TYPE" -q '_elements=subject&_count=500' 2>/dev/null | jq -rc '.subject.reference' | sort -u | grep -c "Patient/$PATIENT_ID")" "0"
done
