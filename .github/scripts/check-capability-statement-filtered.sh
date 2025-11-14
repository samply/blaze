#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
capability_statement=$(curl -sH 'Accept: application/fhir+json' "$base/metadata?_elements=status,software")

test "list of found keys" "$(echo "$capability_statement" | jq -c 'keys')" "[\"resourceType\",\"software\",\"status\"]"
