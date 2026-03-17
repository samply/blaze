#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

validate_code() {
  local params="$1"
  local display="$2"
  result=$(curl -sfH 'Accept: application/fhir+json' "$base/CodeSystem/\$lookup?system=http://snomed.info/sct&$params")

  test "display" "$(echo "$result" | jq -r '.parameter[] | select(.name == "display").valueString')" "$display"
}

validate_code "code=900000000000012004" "SNOMED CT model component"
validate_code "code=73212002" "Iodipamide-containing product"
