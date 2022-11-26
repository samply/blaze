#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

test "software name" "$(curl -s "$BASE/metadata" | jq -r .software.name)" "Blaze"
