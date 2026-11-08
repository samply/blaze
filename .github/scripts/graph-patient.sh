#!/bin/bash
set -euo pipefail

#
# This script creates the GraphDefinition `patient-observation-encounter` and
# invokes $graph with it on the patient with the identifier X79746011X. It
# expects the resulting Bundle to contain the expected number of resources per
# resource type without duplicates. It expects the same resources if the
# GraphDefinition is given in a Parameters resource in the body of a POST
# request.
#
# After that, it invokes $graph with the same GraphDefinition on an Observation
# of that patient and expects a 400 response, because the type of the
# Observation doesn't match the type `Patient` of the start node.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

create "$base/GraphDefinition" < "$script_dir/graph/GraphDefinition-patient-observation-encounter.json" > /dev/null

patient_identifier="X79746011X"
patient_id=$(curl -sSfH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
echo "$patient_id"
bundle=$(curl -sSfH 'Accept: application/fhir+json' "$base/Patient/$patient_id/\$graph?graph=patient-observation-encounter")
actual_size=$(echo "$bundle" | jq -r .total)
ids="$(echo "$bundle" | jq -r '.entry[].resource.id')"

test "size" "$actual_size" "195"

test "no duplicates" "$(echo "$ids" | sort -u | wc -l | xargs)" "$(echo "$ids" | wc -l | xargs)"

test "type counts" "$(echo "$bundle" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$script_dir/graph/$patient_identifier-patient-observation-encounter-type-counts.csv")"

post_bundle=$(curl -sSfH 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' \
  -d '{"resourceType": "Parameters", "parameter": [{"name": "graph", "valueCanonical": "patient-observation-encounter"}]}' \
  "$base/Patient/$patient_id/\$graph")

test "POST size" "$(echo "$post_bundle" | jq -r .total)" "195"
test "POST different ids" "$(comm -3 <(echo "$post_bundle" | jq -r '.entry[].resource.id' | sort) <(echo "$ids" | sort) | wc -l | xargs)" "0"

observation_id=$(curl -sSfH 'Accept: application/fhir+json' "$base/Observation?subject=Patient/$patient_id&_count=1" | jq -r '.entry[0].resource.id')
response=$(curl -s -w '%{response_code}' -H 'Accept: application/fhir+json' "$base/Observation/$observation_id/\$graph?graph=patient-observation-encounter")
body="${response%???}"

test "start node type mismatch status code" "${response: -3}" "400"
test "start node type mismatch issue code" "$(echo "$body" | jq -r '.issue[0].code')" "invalid"
test "start node type mismatch diagnostics" "$(echo "$body" | jq -r '.issue[0].diagnostics')" "The type \`Observation\` of the resource \`Observation/$observation_id\` doesn't match the type \`Patient\` of the start node \`patient\`."
