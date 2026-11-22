#!/bin/bash
set -euo pipefail

# Checks that a FHIR request without token is rejected by the Blaze on port $1
# with 401, MSG_AUTH_REQUIRED and a WWW-Authenticate header with the realm $2
# and without error code.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

port="${1:-8080}"
realm="${2:-http://localhost:8090/realms/blaze}"

headers_file=$(mktemp)
body_file=$(mktemp)
trap 'rm -f "$headers_file" "$body_file"' EXIT

test "status" "$(curl -s -D "$headers_file" -o "$body_file" -w '%{response_code}' "http://localhost:${port}/fhir")" "401"
test "message code" "$(jq -r '.issue[0].details.coding[0].code' "$body_file")" "MSG_AUTH_REQUIRED"
test "WWW-Authenticate header" "$(www_authenticate "$headers_file")" "Bearer realm=\"$realm\""
