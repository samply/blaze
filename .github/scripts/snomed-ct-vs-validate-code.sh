#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

validate_code() {
  local expr="$1"
  local params="$2"
  local display="$3"
  result=$(curl -sH 'Accept: application/fhir+json' "$base/ValueSet/\$validate-code?url=http://fhir.org/VCL?v1=(http://snomed.info/sct)$expr&inferSystem=true&$params")

  test "result" "$(echo "$result" | jq -r '.parameter[] | select(.name == "result").valueBoolean')" "true"
  test "display" "$(echo "$result" | jq -r '.parameter[] | select(.name == "display").valueString')" "$display"
}

validate_code "concept<<119297000" "code=258580003" "Whole blood specimen"
