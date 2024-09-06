#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

URL="$BASE/Patient?name=Le%C3%B3n"
TOTAL=$(curl -s -H 'Accept: application/fhir+json' "$URL&_summary=count" | jq .total)
HEADERS=$(curl -s -H 'Accept: application/fhir+json' -o /dev/null -D - "$URL")
LINK_HEADER=$(echo "$HEADERS" | grep -i link | tr -d '\r')

test "number of patients found" "$TOTAL" "1"
test "encoded search param value" "$(echo "$LINK_HEADER" | awk -F'[;,<>?&=]' '{print $9}')" "Le%C3%B3n"

URL="$BASE/Condition?code=59621000,10509002"
TOTAL=$(curl -s -H 'Accept: application/fhir+json' "$URL&_summary=count" | jq .total)
HEADERS=$(curl -s -H 'Accept: application/fhir+json' -o /dev/null -D - "$URL")
LINK_HEADER=$(echo "$HEADERS" | grep -i link | tr -d '\r')

test "number of conditions found" "$TOTAL" "93"
test "encoded search param value" "$(echo "$LINK_HEADER" | awk -F'[;,<>?&=]' '{print $9}')" "59621000%2C10509002"
