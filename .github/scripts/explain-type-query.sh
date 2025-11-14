#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1
query="${2//[[:space:]]/}"
expected_plan=$3
actual_plan=$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/$type?$query&__explain=true" | jq -r '.entry[0].resource.issue[0].diagnostics')

test "plan" "$actual_plan" "$expected_plan"
