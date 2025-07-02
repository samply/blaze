#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
QUERY="${2//[[:space:]]/}"
EXPECTED_SIZE=$3
ACTUAL_SIZE=$(blazectl --server "$BASE" download "$TYPE" -q "$QUERY" 2>/dev/null | jq -r .subject.reference | sort -u | wc -l | xargs)

test "number of patients" "$ACTUAL_SIZE" "$EXPECTED_SIZE"
