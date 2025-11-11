#!/bin/bash -e

#
# This script creates an observation with a string value containing the NUL char
# and reads it back in JSON and XML. The JSON variant contains the escaped NUL
# char in the form of \u0000 were the XML variant contains a question mark (?)
# because escaping of control chars isn't possible in XML 1.0.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

JSON_RES=$(echo '{"resourceType": "Observation", "valueString": "\u0000" }' | create "$BASE/Observation")
ID="$(echo "$JSON_RES" | jq -r .id)"

test "JSON value" "$(echo "$JSON_RES" | jq .valueString)" "\"\u0000\""

XML_RES=$(curl -s -H 'Accept: application/fhir+xml' "$BASE/Observation/$ID")

test "XML value" "$(echo "$XML_RES" | xq -x /Observation/valueString/@value)" "?"
