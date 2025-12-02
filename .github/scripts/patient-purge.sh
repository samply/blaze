#!/bin/bash -e

#
# This script verifies that the patient and all resources that are part of the
# patient compartment are purged.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X26238298X"
patient_id=$(curl -s "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')

echo "calling \$purge via GET should not be allowed"
test "GET response code" "$(curl -s -o /dev/null -w '%{response_code}' "$base/Patient/$patient_id/\$purge")" "405"

outcome="$(curl -s -XPOST "$base/Patient/$patient_id/\$purge")"

test "outcome code" "$(echo "$outcome" | jq -r '.issue[0].code')" "success"
test "read patient response code" "$(curl -s -o /dev/null -w '%{response_code}' "$base/Patient/$patient_id")" "404"

for type in "CarePlan" \
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
  test "number of references from ${type}s to the purged patient" "$(blazectl --server "$base" download "$type" -q '_elements=subject&_count=500' 2>/dev/null | jq -rc '.subject.reference' | sort -u | grep -c "Patient/$patient_id")" "0"
done
