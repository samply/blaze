#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TERMINOLOGY_CAPABILITIES=$(curl -sH 'Accept: application/fhir+json' "$BASE/metadata?mode=terminology")

test "resourceType" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r .resourceType)" "TerminologyCapabilities"
test "status" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r .status)" "active"
test "kind" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r .kind)" "instance"
test "software name" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r .implementation.url)" "http://localhost:8080/fhir"

test "BCP-13 version" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r '.codeSystem[] | select(.uri == "urn:ietf:bcp:13").version[0].code' )" "1.0.0"
test "LOINC version" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r '.codeSystem[] | select(.uri == "http://loinc.org").version[0].code' )" "2.78"
test "UCUM version" "$(echo "$TERMINOLOGY_CAPABILITIES" | jq -r '.codeSystem[] | select(.uri == "http://unitsofmeasure.org").version[0].code' )" "2013.10.21"
