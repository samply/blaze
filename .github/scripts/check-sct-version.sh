#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

version="$1"
base="${2:-http://localhost:8080/fhir}"
terminology_capabilities=$(curl -sH 'Accept: application/fhir+json' "$base/metadata?mode=terminology")

test "SCT version" "$(echo "$terminology_capabilities" | jq -r '.codeSystem[] | select(.uri == "http://snomed.info/sct").version[0].code' )" "$version"
