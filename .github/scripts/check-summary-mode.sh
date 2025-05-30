#!/bin/bash -e

# Checks that resources of type $1 have no property $2, if retrieved with _summary=true.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE="$1"
PROPERTY="$2"
NORMAL_RESULT=$(blazectl --server "$BASE" download "$TYPE" 2>/dev/null | jq -c ".$PROPERTY | values")
SUMMARY_RESULT=$(blazectl --server "$BASE" download "$TYPE" -q '_summary=true' 2>/dev/null | jq ".$PROPERTY | values")

test_non_empty "normal result" "$NORMAL_RESULT"
test_empty "summary result" "$SUMMARY_RESULT"
