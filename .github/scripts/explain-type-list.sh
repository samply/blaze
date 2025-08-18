#!/bin/bash -e

# Tests that __explain on a search-type interaction without query params is ignored.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
NUM_OUTCOME_ENTRIES=$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/$TYPE?__explain=true" | jq -r '[.entry[] | select(.search.mode == "outcome")] | length')

test "#outcome entries" "$NUM_OUTCOME_ENTRIES" "0"
