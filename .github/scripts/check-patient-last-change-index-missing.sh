#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
curl -s "$BASE/__admin/dbs/index/column-families" | jq -r '.[].name' | grep -q "patient-last-change-index"

test "exit code" "$?" "1"
