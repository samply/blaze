#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

validate_code() {
  local params="$1"
  local display="$2"
  result=$(curl -sH 'Accept: application/fhir+json' "$base/CodeSystem/\$validate-code?url=http://snomed.info/sct&$params")

  test "result" "$(echo "$result" | jq -r '.parameter[] | select(.name == "result").valueBoolean')" "true"
  test "display" "$(echo "$result" | jq -r '.parameter[] | select(.name == "display").valueString')" "$display"
}

validate_code "code=900000000000012004" "SNOMED CT model component"
validate_code "version=http://snomed.info/sct/11000274103&code=11000274103" "German module"
validate_code "version=http://snomed.info/sct/11000274103&code=31000274107" "Germany language reference set"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008" "Streptococcal sore throat"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008&displayLanguage=de" "Streptokokken-Halsentzündung"
validate_code "version=http://snomed.info/sct/11000274103&code=43878008&display=Streptokokken-Halsentzündung" "Streptococcal sore throat"
validate_code "code=73212002" "Iodipamide-containing product"
