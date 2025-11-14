#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
actual_totals="$(curl -sH 'Accept: application/fhir+json' "$base/\$totals" | jq -r '.parameter[] | [.name, .valueUnsignedInt] | @csv')"
expected_totals="$(cat <<END
"AllergyIntolerance",76
"CarePlan",540
"CareTeam",540
"Claim",9856
"Condition",1597
"Device",28
"DiagnosticReport",8229
"DocumentReference",4769
"Encounter",4769
"ExplanationOfBenefit",4769
"ImagingStudy",145
"Immunization",1616
"Location",194
"Medication",326
"MedicationAdministration",326
"MedicationRequest",5087
"Observation",42929
"Organization",194
"Patient",120
"Practitioner",195
"PractitionerRole",195
"Procedure",3608
"Provenance",120
"StructureDefinition",185
"SupplyDelivery",1886
END
)"

test "resource totals" "$actual_totals" "$expected_totals"
