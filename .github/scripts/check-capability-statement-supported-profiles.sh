#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
CAPABILITY_STATEMENT=$(curl -sH 'Accept: application/fhir+json' "$BASE/metadata")

test "Patient Supported Profile" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "Patient") .supportedProfile[0]')" "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient|2025.0.0"
