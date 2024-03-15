#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

ENCOUNTER_ID_LIST=$(blazectl --server "$BASE" download Encounter -q '_elements=id&_count=1000' 2>/dev/null | jq -r .id | tr '\n' ',')
NUM_OBSERVATIONS=$(blazectl --server "$BASE" download Observation -p -q "encounter=$ENCOUNTER_ID_LIST&_count=1000" 2>/dev/null | wc -l | xargs)

test "number of Observations" "$NUM_OBSERVATIONS" "42929"
