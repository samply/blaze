#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

create "$base/GraphDefinition" < "$script_dir/graph/GraphDefinition-patient-observation-encounter.json" > /dev/null

patient_identifier="X79746011X"
patient_id=$(curl -sH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
echo "$patient_id"
bundle=$(curl -sH 'Accept: application/fhir+json' "$base/Patient/$patient_id/\$graph?graph=patient-observation-encounter")
actual_size=$(echo "$bundle" | jq -r .total)
ids="$(echo "$bundle" | jq -r '.entry[].resource.id')"

test "size" "$actual_size" "195"

test "no duplicates" "$(echo "$ids" | sort -u | wc -l | xargs)" "$(echo "$ids" | wc -l | xargs)"

test "type counts" "$(echo "$bundle" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$script_dir/graph/$patient_identifier-patient-observation-encounter-type-counts.csv")"
