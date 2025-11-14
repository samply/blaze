#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
test "total number of resources" "$(curl -sH 'Accept: application/fhir+json' "$base" | jq -r .total)" "$1"
