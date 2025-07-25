#!/bin/bash -e

# Returns the patient IDs if patient with identifiers taken as first argument.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIERS="${1//[[:space:]]/}"
curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIERS&_count=1000" | jq -r '.entry[].resource.id' | paste -sd, -
