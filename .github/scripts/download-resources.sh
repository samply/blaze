#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
EXPECTED_SIZE=$(curl -s "$BASE/${TYPE}?_summary=count" | jq -r .total)

FILE_NAME=$(uuidgen)
blazectl --no-progress --server "$BASE" download "$TYPE" -o "$FILE_NAME.ndjson"

test "download size" "$(wc -l "$FILE_NAME.ndjson" | xargs | cut -d ' ' -f1)" "$EXPECTED_SIZE"
