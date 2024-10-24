#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
ACTUAL_TOTALS="$(curl -sH 'Accept: application/fhir+json' "$BASE/\$totals" | jq -r '.parameter[] | [.name, .valueUnsignedInt] | @csv')"
EXPECTED_TOTALS="$(cat <<END
"AllergyIntolerance",2
"CarePlan",33
"CareTeam",33
"Claim",1053
"Condition",157
"Device",28
"DiagnosticReport",1113
"DocumentReference",407
"Encounter",407
"ExplanationOfBenefit",407
"Immunization",104
"Location",194
"Medication",326
"MedicationAdministration",26
"MedicationRequest",646
"Observation",6944
"Organization",194
"Patient",8
"Practitioner",195
"PractitionerRole",195
"Procedure",307
"Provenance",8
"SupplyDelivery",571
END
)"

test "resource totals" "$ACTUAL_TOTALS" "$EXPECTED_TOTALS"
