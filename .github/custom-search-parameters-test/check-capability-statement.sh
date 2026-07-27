#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
capability_statement=$(curl -sfH 'Accept: application/fhir+json' "$base/metadata")

name() {
  echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Patient") .searchParam[] | select(.definition == "https://samply.github.io/blaze/fhir/SearchParameter/Patient-marital-status") .name'
}

test "Patient marital-status search param name" "$(name)" "marital-status"
