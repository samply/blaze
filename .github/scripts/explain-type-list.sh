#!/bin/bash -e

# Tests that __explain on a search-type interaction without query params is ignored.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1
num_outcome_entries=$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/$type?__explain=true" | jq -r '[.entry[] | select(.search.mode == "outcome")] | length')

test "#outcome entries" "$num_outcome_entries" "0"
