#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"
COLUMN_FAMILY="$1"
EXPECTED_ESTIMATE_NUM_KEYS="$2"
ACTUAL_ESTIMATE_NUM_KEYS=$(curl -s "$BASE/__admin/dbs/index/column-families" | jq ".[] | select(.name == \"$COLUMN_FAMILY\").estimateNumKeys")

test "estimate number of keys" "$ACTUAL_ESTIMATE_NUM_KEYS" "$EXPECTED_ESTIMATE_NUM_KEYS"
