#!/bin/bash
set -euo pipefail

#
# This script creates the GraphDefinition `encounter-all` and invokes $graph
# with it on the encounter with the identifier
# fa8b0215-735f-7352-e53b-054606ab6da4. It expects the resulting Bundle to
# contain all resources reachable from that encounter, with the expected number
# of resources per resource type and without duplicates.
#
# The GraphDefinition follows the reference of the encounter to its patient and
# the references to the encounter from all resource types of the test data
# having a search parameter for it. Immunization isn't included, because it has
# no such search parameter. The practitioner, organization and location of the
# encounter aren't included, because the test data refers to them by
# conditional references, which aren't literal references.
#
# Finally, it searches GraphDefinitions by start type the way the frontend does
# for an encounter and expects to find `encounter-all`, but not the
# GraphDefinitions starting at a patient.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

create "$base/GraphDefinition" < "$script_dir/graph/GraphDefinition-encounter-all.json" > /dev/null

encounter_identifier="fa8b0215-735f-7352-e53b-054606ab6da4"
encounter_id=$(curl -sSfH 'Accept: application/fhir+json' "$base/Encounter?identifier=$encounter_identifier" | jq -r '.entry[0].resource.id')
echo "$encounter_id"
bundle=$(curl -sSfH 'Accept: application/fhir+json' "$base/Encounter/$encounter_id/\$graph?graph=encounter-all")
actual_size=$(echo "$bundle" | jq -r .total)
ids="$(echo "$bundle" | jq -r '.entry[].resource.id')"

test "size" "$actual_size" "17"

test "no duplicates" "$(echo "$ids" | sort -u | wc -l | xargs)" "$(echo "$ids" | wc -l | xargs)"

test "type counts" "$(echo "$bundle" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$script_dir/graph/$encounter_identifier-encounter-all-type-counts.csv")"

test "GraphDefinitions starting at an encounter" "$(curl -sSfH 'Accept: application/fhir+json' "$base/GraphDefinition?start=Encounter,DomainResource,Resource&_summary=true" | jq -r '[.entry[].resource.url] | join(",")')" "encounter-all"
