#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

validate_code() {
  PARAMS="$1"
  DISPLAY="$2"
  RESULT=$(curl -sH 'Accept: application/fhir+json' "$BASE/CodeSystem/\$validate-code?url=http://snomed.info/sct&$PARAMS")

  test "result" "$(echo "$RESULT" | jq -r '.parameter[] | select(.name == "result").valueBoolean')" "true"
  test "display" "$(echo "$RESULT" | jq -r '.parameter[] | select(.name == "display").valueString')" "$DISPLAY"
}

validate_code "code=900000000000012004" "SNOMED CT model component module (core metadata concept)"
validate_code "version=http://snomed.info/sct/11000274103&code=11000274103" "Germany National Extension module (core metadata concept)"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008" "Streptococcal sore throat (disorder)"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008&display=Streptokokken-Halsentzündung" "Streptococcal sore throat (disorder)"
