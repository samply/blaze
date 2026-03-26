#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1
expected_size=$(curl -sfH 'Accept: application/fhir+json' "$base/${type}?_summary=count" | jq -r .total)
actual_size=$(curl -sfH "Content-Type: application/graphql" -d "{ ${type}List { id } }" "$base/\$graphql" | jq ".data.${type}List | length")

test "size" "$actual_size" "$expected_size"
