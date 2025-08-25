#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
ACTUAL_TOTALS="$(curl -sH 'Accept: application/fhir+json' "$BASE/\$totals" | jq -r '.parameter[] | [.name, .valueUnsignedInt] | @csv')"
EXPECTED_TOTALS="$(cat <<END
"AllergyIntolerance",76
"CarePlan",540
"CareTeam",540
"Claim",9856
"Condition",1597
"DiagnosticReport",8229
"DocumentReference",4769
"Encounter",4769
"ExplanationOfBenefit",4769
"Immunization",1616
"Location",194
"Medication",326
"MedicationRequest",5087
"Observation",42929
"Organization",194
"Patient",120
"Practitioner",195
"PractitionerRole",195
"Procedure",3608
"Provenance",120
"StructureDefinition",204
"SupplyDelivery",1886
END
)"

test "resource totals" "$ACTUAL_TOTALS" "$EXPECTED_TOTALS"
