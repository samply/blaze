#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
HASH="$1"
PATIENT_COUNT="$2"

test "patient count" "$(curl -s "$BASE/__admin/cql/bloom-filters" | jq -r ".[] | select(.hash == \"$HASH\") | .patientCount")" "$PATIENT_COUNT"
