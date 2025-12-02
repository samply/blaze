#!/bin/bash -e

#
# This script creates an observation with a string value containing the NUL char
# and reads it back in JSON and XML. The JSON variant contains the escaped NUL
# char in the form of \u0000 were the XML variant contains a question mark (?)
# because escaping of control chars isn't possible in XML 1.0.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

json_res=$(echo '{"resourceType": "Observation", "valueString": "\u0000" }' | create "$base/Observation")
ID="$(echo "$json_res" | jq -r .id)"

test "JSON value" "$(echo "$json_res" | jq .valueString)" "\"\u0000\""

xml_res=$(curl -s -H 'Accept: application/fhir+xml' "$base/Observation/$ID")

test "XML value" "$(echo "$xml_res" | xq -x /Observation/valueString/@value)" "?"
