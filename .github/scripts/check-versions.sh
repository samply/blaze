#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="${1:-http://localhost:8080/fhir}"

response="$(curl -sfH 'Accept: application/fhir+json' "$base/\$versions")"

test "resourceType" "$(echo "$response" | jq -r .resourceType)" "Parameters"
test "version" "$(echo "$response" | jq -r '.parameter[] | select(.name == "version") | .valueString')" "4.0"
test "default" "$(echo "$response" | jq -r '.parameter[] | select(.name == "default") | .valueString')" "4.0"
