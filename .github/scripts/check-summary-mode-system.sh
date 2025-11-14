#!/bin/bash -e

# Checks that resources of type $1 have no property $2, if retrieved with _summary=true using the search-system interaction.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type="$1"
property="$2"
normal_result=$(blazectl --server "$base" download 2>/dev/null | jq -c "select(.resourceType == \"$type\").$property | values")
summary_result=$(blazectl --server "$base" download -q '_summary=true' 2>/dev/null | jq "select(.resourceType == \"$type\").$property | values")

test_non_empty "normal result" "$normal_result"
test_empty "summary result" "$summary_result"
