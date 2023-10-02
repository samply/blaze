#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
STATE="$(curl -s "$BASE/__admin/db/index/column-families/patient-last-change-index/state" | jq -r .type)"

test "state" "$STATE" "$1"
