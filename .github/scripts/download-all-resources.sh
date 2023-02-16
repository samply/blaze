#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
EXPECTED_SIZE=$(curl -s "$BASE?_summary=count" | jq -r .total)

FILE_NAME=$(uuidgen)
blazectl --no-progress --server $BASE download "" -o "$FILE_NAME".ndjson

test "download size" "$(wc -l "$FILE_NAME".ndjson | xargs | cut -d ' ' -f1)" "$EXPECTED_SIZE"
