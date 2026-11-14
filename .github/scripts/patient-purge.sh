#!/bin/bash
set -euo pipefail

#
# This script verifies that the patient and all resources about it are purged.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X26238298X"
patient_id=$(curl -sSfH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')

total() {
  curl -sSfH 'Accept: application/fhir+json' "$base?_summary=count" | jq -r .total
}

# The $graph operation with the purge GraphDefinition selects exactly the
# resources a purge erases
purge_graph_url="https://blaze-server.org/fhir/GraphDefinition/PatientPurge"
graph_refs="$(curl -sSfH 'Accept: application/fhir+json' "$base/Patient/$patient_id/\$graph?graph=$purge_graph_url" | jq -r '.entry[].resource | "\(.resourceType)/\(.id)"')"
test_non_empty "list of resources selected by the purge GraphDefinition" "$graph_refs"
total_before="$(total)"

echo "calling \$purge via GET should not be allowed"
test "GET response code" "$(curl -sS -H 'Accept: application/fhir+json' -o /dev/null -w '%{response_code}' "$base/Patient/$patient_id/\$purge")" "405"

echo "calling \$purge via POST..."
outcome="$(curl -sSfH 'Accept: application/fhir+json' -XPOST "$base/Patient/$patient_id/\$purge")"

test "outcome code" "$(echo "$outcome" | jq -r '.issue[0].code')" "success"
test "read patient response code" "$(curl -sS -H 'Accept: application/fhir+json' -o /dev/null -w '%{response_code}' "$base/Patient/$patient_id")" "404"

readable_refs="$(for ref in $graph_refs; do
  curl -sS -H 'Accept: application/fhir+json' -o /dev/null -w "%{response_code} $ref\n" "$base/$ref"
done | grep -v '^404 ' || true)"
test_empty "list of selected resources still readable" "$readable_refs"

test "number of purged resources" "$((total_before - $(total)))" "$(echo "$graph_refs" | wc -l | xargs)"

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
  test "number of references from ${type}s to the purged patient" "$(blazectl --server "$base" download "$type" -q '_elements=subject&_count=1000' 2>/dev/null | jq -rc '.subject.reference' | sort -u | grep -c "Patient/$patient_id")" "0"
done
