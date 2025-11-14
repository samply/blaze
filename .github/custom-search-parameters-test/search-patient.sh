#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"

count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/Patient?marital-status=M" | jq .total
}

test "number of patients" "$(count)" "1"
