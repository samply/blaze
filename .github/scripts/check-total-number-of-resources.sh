#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

total() {
  curl -sSH 'Accept: application/fhir+json' "$1" | jq -r .total
}

# Without the GraphDefinition the Patient $purge operation installs at startup
test "total number of resources" "$(($(total "$base") - $(total "$base/GraphDefinition?_summary=count")))" "$1"
