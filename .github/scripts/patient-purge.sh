#!/bin/bash
set -euo pipefail

#
# This script does the following:
#
#  * updates the patient and one of its observations to create history entries
#  * updates another patient, so that it's the most recently updated one
#  * opens a paging session with the patient on the second page
#  * expects calling $purge via GET to be not allowed
#  * purges the patient
#  * expects the second page of the paging session to still contain the patient
#  * expects a new search to no longer contain the patient
#  * expects the read, versioned reads and the instance history of the patient
#    and of the observation to return 404
#  * expects the type and system history to contain no entries of the patient
#    and of the observation
#  * expects $everything to return 404 and the compartment search to be empty
#  * expects no resource to reference the patient
#  * expects the resource totals to be decreased by exactly the number of
#    purged resources
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
  curl -sfH 'Accept: application/fhir+json' "$base/$1"
}

status_code() {
  curl -s -o /dev/null -w '%{response_code}' "$base/$1"
}

# Updates the resource read from stdin, which has to differ from the current
# version, in order to create a new history entry.
update_resource() {
  update "$base/$1" > /dev/null
}

instance_history_total() {
  read_resource "$1/_history" | jq -r '.total'
}

# Returns the version id of the oldest history entry of the resource $1.
first_version_id() {
  read_resource "$1/_history" | jq -r '.entry[-1].resource.meta.versionId'
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
history_file="$tmp_dir/history.ndjson"

# Downloads the history since $since of the resource type $1, or of the whole
# system if $1 is omitted, into $history_file. Fails if blazectl fails.
download_recent_history() {
  rm -f "$history_file"
  blazectl --no-progress --server "$base" download-history "$@" --since "$since" --output-file "$history_file" > /dev/null
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
other_patient_id=$(read_resource "Patient?_elements=id&_count=2" | jq -r --arg id "$patient_id" '[.entry[].resource.id | select(. != $id)][0]')
patient_ref="Patient/$patient_id"
observation_ref="Observation/$observation_id"
other_patient_ref="Patient/$other_patient_id"
since="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"

echo "ℹ️ purge $patient_ref with $observation_ref"

echo "▶️ update the patient and the observation to create history entries"
read_resource "$patient_ref" | jq '.active = (.active != true)' | update_resource "$patient_ref"
read_resource "$observation_ref" | jq '.status = "amended"' | update_resource "$observation_ref"

echo "▶️ update $other_patient_ref, so that it's the most recently updated patient"
read_resource "$other_patient_ref" | jq '.active = (.active != true)' | update_resource "$other_patient_ref"

test "instance history total of the patient" "$(instance_history_total "$patient_ref")" "2"
test "instance history total of the observation" "$(instance_history_total "$observation_ref")" "2"
download_recent_history "Patient"
test "number of type history entries of the patient" "$(num_history_entries "$patient_ref")" "1"
download_recent_history
test "number of system history entries of the patient and the observation" "$(num_history_entries "$patient_ref" "$observation_ref")" "2"
patient_vid="$(first_version_id "$patient_ref")"
observation_vid="$(first_version_id "$observation_ref")"

echo "▶️ count the resources referencing the patient"
declare -A expected_purged=(["Patient"]=1)
for type in "${types[@]}"; do
  count="$(count_referencing "$type")"
  echo "ℹ️ $count ${type}(s)"
  if [ "$count" -gt 0 ]; then
    expected_purged["$type"]="$count"
  fi
done
test "Device count" "${expected_purged["Device"]:-0}" "0"
totals_before="$(totals)"

echo "▶️ open a paging session with the patient on the second page"
first_page="$(search_strict "$base/Patient?_sort=-_lastUpdated&_count=1")"
test "patient id on the first page" "$(echo "$first_page" | jq -r '.entry[0].resource.id')" "$other_patient_id"
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"

echo "▶️ calling \$purge via GET should not be allowed"
test "GET response code" "$(status_code "$patient_ref/\$purge")" "405"

echo "▶️ calling \$purge via POST..."
outcome="$(curl -sfH 'Accept: application/fhir+json' -XPOST "$base/$patient_ref/\$purge")"

test "outcome code" "$(echo "$outcome" | jq -r '.issue[0].code')" "success"

echo "▶️ follow the next link of the paging session opened before the purge"
second_page="$(curl -sfH 'Accept: application/fhir+json' "$next_link")"
test "patient id on the second page" "$(echo "$second_page" | jq -r '.entry[0].resource.id')" "$patient_id"
test "number of entries of the patient in a new search" "$(search_strict "$base/Patient?_sort=-_lastUpdated&_count=2" | jq --arg id "$patient_id" '[.entry[]? | .resource.id | select(. == $id)] | length')" "0"

test "read patient response code" "$(status_code "$patient_ref")" "404"
test "read observation response code" "$(status_code "$observation_ref")" "404"
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

expected_purged_json="$(for type in "${!expected_purged[@]}"; do echo "{\"$type\": ${expected_purged["$type"]}}"; done | jq -sc 'add')"
actual_purged_json="$(jq -nc --argjson before "$totals_before" --argjson after "$(totals)" \
  '$before | with_entries(.value -= ($after[.key] // 0)) | with_entries(select(.value != 0))')"
test "number of purged resources by type" "$(echo "$actual_purged_json" | jq -S .)" "$(echo "$expected_purged_json" | jq -S .)"
