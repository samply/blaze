#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"

count() {
  search_strict "$base/Patient?marital-status=M" | jq .total
}

test "number of patients" "$(count)" "1"
