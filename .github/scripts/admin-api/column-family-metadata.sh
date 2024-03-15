#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"
COLUMN_FAMILY=$1
ACTUAL_NAME=$(curl -s "$BASE/__admin/dbs/index/column-families/$COLUMN_FAMILY/metadata" | jq -r .name)

test "column family name" "$ACTUAL_NAME" "$COLUMN_FAMILY"
