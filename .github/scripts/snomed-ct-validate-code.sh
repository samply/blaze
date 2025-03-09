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

validate_code "code=900000000000012004" "SNOMED CT model component"
validate_code "version=http://snomed.info/sct/11000274103&code=11000274103" "German module"
validate_code "version=http://snomed.info/sct/11000274103&code=31000274107" "Germany language reference set"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008" "Streptococcal sore throat"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008&displayLanguage=de" "Streptokokken-Halsentzündung"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008&display=Streptokokken-Halsentzündung" "Streptococcal sore throat"
validate_code "code=73212002" "Iodipamide-containing product"
