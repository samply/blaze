#!/bin/bash
set -euo pipefail

#
# This script verifies that the patient and all resources about it are purged.
#
# It does the following:
#
#  * updates the patient and one of its observations to create history entries
#  * creates a helper patient, so that it's the most recently updated one
#  * deletes the Provenances targeting the patient
#  * selects the resources a purge erases with the purge GraphDefinition
#  * opens a paging session with the patient on the second page
#  * expects calling $purge via GET to be not allowed
#  * purges the patient
#  * expects the second page of the paging session to still contain the patient
#  * expects a new search to no longer contain the patient
#  * expects none of the selected resources to be readable
#  * expects the versioned reads and the instance history of the patient and of
#    the observation to return 404
#  * expects the type and system history to contain no entries of the patient
#    and of the observation
#  * expects $everything to return 404 and the compartment search to be empty
#  * expects no resource to reference the patient
#  * expects the resource totals to be decreased by exactly the number of
#    selected resources of each type
#  * purges the helper patient, so that no trace of it remains
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X26238298X"

# All resource types of the test data which have a patient search param.
types=(
  "AllergyIntolerance"
  "CarePlan"
  "CareTeam"
  "Claim"
  "Condition"
  "Device"
  "DiagnosticReport"
  "DocumentReference"
  "Encounter"
  "ExplanationOfBenefit"
  "ImagingStudy"
  "Immunization"
  "MedicationAdministration"
  "MedicationRequest"
  "Observation"
  "Procedure"
  "Provenance"
  "SupplyDelivery"
)

read_resource() {
  curl -sSfH 'Accept: application/fhir+json' "$base/$1"
}

status_code() {
  curl -sS -H 'Accept: application/fhir+json' -o /dev/null -w '%{response_code}' "$base/$1"
}

# Updates the resource read from stdin, which has to differ from the current
# version, in order to create a new history entry.
update_resource() {
  update "$base/$1" > /dev/null
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
history_file="$tmp_dir/history.ndjson"

# Purges the patient with id $1. Returns the response code, followed by the code
# and the diagnostics of the first issue of the returned OperationOutcome, so
# that a failing purge shows its reason, like the resources still referencing
# the patient.
purge() {
  local outcome_file="$tmp_dir/outcome-$1.json"
  local code
  code="$(curl -sS -H 'Accept: application/fhir+json' -X POST -o "$outcome_file" -w '%{response_code}' "$base/Patient/$1/\$purge")"
  echo "$code $(jq -r '.issue[0] | "\(.code): \(.diagnostics)"' "$outcome_file")"
}

# Downloads the history since $since of the resource type $1, or of the whole
# system if $1 is omitted, into $history_file. Fails if blazectl fails.
download_recent_history() {
  rm -f "$history_file"
  blazectl -sS --server "$base" download-history "$@" --since "$since" --output-file "$history_file"
}

# Returns the number of entries of the history in $history_file which belong
# to the resources with the references $1...
num_history_entries() {
  jq -n --args '[inputs | "\(.resourceType)/\(.id)" | select(IN($ARGS.positional[]))] | length' "$@" < "$history_file"
}

count_referencing() {
  search_strict "$base/$1?patient=Patient/$patient_id&_summary=count" | jq -r '.total'
}

totals() {
  read_resource "\$totals" | jq -c '[.parameter[] | {(.name): .valueUnsignedInt}] | add'
}

patient_id=$(read_resource "Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
observation_id=$(read_resource "Observation?patient=Patient/$patient_id&_count=1" | jq -r '.entry[0].resource.id')
patient_ref="Patient/$patient_id"
observation_ref="Observation/$observation_id"
since="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"

echo "ℹ️ purge $patient_ref with $observation_ref"

echo "▶️ update the patient and the observation to create history entries"
read_resource "$patient_ref" | jq '.active = (.active != true)' | update_resource "$patient_ref"
read_resource "$observation_ref" | jq '.status = "amended"' | update_resource "$observation_ref"

echo "▶️ create a helper patient, so that it's the most recently updated patient"
helper_patient_id="$(echo '{"resourceType": "Patient"}' | create "$base/Patient" | jq -r '.id')"

test "instance history total of the patient" "$(fetch_instance_history "$base" Patient "$patient_id" | jq -r '.total')" "2"
test "instance history total of the observation" "$(fetch_instance_history "$base" Observation "$observation_id" | jq -r '.total')" "2"
download_recent_history "Patient"
test "number of type history entries of the patient" "$(num_history_entries "$patient_ref")" "1"
download_recent_history
test "number of system history entries of the patient and the observation" "$(num_history_entries "$patient_ref" "$observation_ref")" "2"
patient_vid="$(fetch_instance_history "$base" Patient "$patient_id" | jq -r '.entry[-1].resource.meta.versionId')"
observation_vid="$(fetch_instance_history "$base" Observation "$observation_id" | jq -r '.entry[-1].resource.meta.versionId')"

# A Provenance isn't purged, because it can target several patients. The one
# Synthea creates for each patient targets only that patient, so it's deleted
# before the purge, which would fail otherwise. The conditional delete also
# succeeds if an earlier step already deleted it.
echo "▶️ delete the Provenances targeting the patient"
test "Provenance delete response code" "$(curl -sSf -H 'Accept: application/fhir+json' -X DELETE -o /dev/null -w '%{response_code}' "$base/Provenance?patient=$patient_id")" "204"
test "number of Provenances targeting the patient" "$(count_referencing "Provenance")" "0"

# The $graph operation with the purge GraphDefinition selects exactly the
# resources a purge erases
purge_graph_url="https://blaze-server.org/fhir/GraphDefinition/PatientPurge"
graph_refs="$(read_resource "$patient_ref/\$graph?graph=$purge_graph_url" | jq -r '.entry[].resource | "\(.resourceType)/\(.id)"')"
test_non_empty "list of resources selected by the purge GraphDefinition" "$graph_refs"
test "number of selections of the observation" "$(echo "$graph_refs" | grep -cxF "$observation_ref")" "1"
expected_purged="$(echo "$graph_refs" | jq -Rn '[inputs | split("/")[0]] | group_by(.) | map({(.[0]): length}) | add')"
echo "ℹ️ selected resources: $(echo "$expected_purged" | jq -c .)"
totals_before="$(totals)"

echo "▶️ open a paging session with the patient on the second page"
first_page="$(search_strict "$base/Patient?_sort=-_lastUpdated&_count=1")"
test "patient id on the first page" "$(echo "$first_page" | jq -r '.entry[0].resource.id')" "$helper_patient_id"
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"

echo "▶️ calling \$purge via GET should not be allowed"
test "GET response code" "$(status_code "$patient_ref/\$purge")" "405"

echo "▶️ calling \$purge via POST..."
test "purge outcome" "$(purge "$patient_id")" "200 success: The patient with id \`$patient_id\` was purged successfully."

echo "▶️ follow the next link of the paging session opened before the purge"
second_page="$(curl -sSfH 'Accept: application/fhir+json' "$next_link")"
test "patient id on the second page" "$(echo "$second_page" | jq -r '.entry[0].resource.id')" "$patient_id"
test "number of entries of the patient in a new search" "$(search_strict "$base/Patient?_sort=-_lastUpdated&_count=2" | jq --arg id "$patient_id" '[.entry[]? | .resource.id | select(. == $id)] | length')" "0"

readable_refs="$(for ref in $graph_refs; do
  echo "$(status_code "$ref") $ref"
done | grep -v '^404 ' || true)"
test_empty "list of selected resources still readable" "$readable_refs"

test "versioned read patient response code" "$(status_code "$patient_ref/_history/$patient_vid")" "404"
test "versioned read observation response code" "$(status_code "$observation_ref/_history/$observation_vid")" "404"
test "patient instance history response code" "$(status_code "$patient_ref/_history")" "404"
test "observation instance history response code" "$(status_code "$observation_ref/_history")" "404"
download_recent_history "Patient"
test "number of type history entries of the patient" "$(num_history_entries "$patient_ref")" "0"
download_recent_history
test "number of system history entries of the patient and the observation" "$(num_history_entries "$patient_ref" "$observation_ref")" "0"
test "\$everything response code" "$(status_code "$patient_ref/\$everything")" "404"
test "compartment search total" "$(search_strict "$base/$patient_ref/Observation?_summary=count" | jq -r '.total')" "0"

for type in "${types[@]}"; do
  test "number of ${type}s referencing the purged patient" "$(count_referencing "$type")" "0"
done

actual_purged="$(jq -nc --argjson before "$totals_before" --argjson after "$(totals)" \
  '$before | with_entries(.value -= ($after[.key] // 0)) | with_entries(select(.value != 0))')"
test "number of purged resources by type" "$(echo "$actual_purged" | jq -S .)" "$(echo "$expected_purged" | jq -S .)"

echo "▶️ purge the helper patient"
test "helper patient purge outcome" "$(purge "$helper_patient_id")" "200 success: The patient with id \`$helper_patient_id\` was purged successfully."
