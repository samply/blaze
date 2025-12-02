#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
actual_totals="$(curl -sH 'Accept: application/fhir+json' "$base/\$totals" | jq -r '.parameter[] | [.name, .valueUnsignedInt] | @csv')"
expected_totals="$(cat <<END
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
"StructureDefinition",185
"SupplyDelivery",571
END
)"

test "resource totals" "$actual_totals" "$expected_totals"
