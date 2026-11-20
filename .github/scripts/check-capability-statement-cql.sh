#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="${1:-http://localhost:8080/fhir}"
capability_statement=$(curl -sSfH 'Accept: application/fhir+json' "$base/metadata")

test "Operation \$cql Definition" "$(echo "$capability_statement" | jq -r '.rest[0].operation[] | select(.name == "cql") | .definition')" "http://hl7.org/fhir/uv/cql/OperationDefinition/cql-cql"
