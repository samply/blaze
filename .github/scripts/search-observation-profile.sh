#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
LAB_COUNT=$(curl -s "$BASE/Observation?_profile=http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab&_summary=count" | jq -r .total)

test "lab count" "$LAB_COUNT" "27218"
