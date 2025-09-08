#!/bin/bash -e

# Returns the patient IDs if patient with identifiers taken as first argument.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIERS="${1//[[:space:]]/}"

# it's important to shuffle the IDs here in order to test that FHIR search works with unordered IDs
blazectl download --server "$BASE" Patient -p -q "identifier=$PATIENT_IDENTIFIERS" | jq -r '.id' | shuf | paste -sd, -
