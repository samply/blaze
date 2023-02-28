#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
EXPECTED_SIZE=$(curl -s "$BASE/${TYPE}?_summary=count" | jq -r .total)
ACTUAL_SIZE=$(curl -s -H "Content-Type: application/graphql" -d "{ ${TYPE}List { id } }" "$BASE/\$graphql" | jq ".data.${TYPE}List | length")

test "size" "$ACTUAL_SIZE" "$EXPECTED_SIZE"
