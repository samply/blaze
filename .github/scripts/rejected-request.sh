#!/bin/bash
set -euo pipefail

# Checks that a valid Keycloak token is rejected by the Blaze on port $1 with
# 401 and a WWW-Authenticate header with the realm $2 and error invalid_token.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

port="${1:-8081}"
realm="${2:-http://localhost:8090/realms/blaze}"

access_token=$(curl -sSfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)
test_non_empty "access token" "$access_token"

headers_file=$(mktemp)
trap 'rm -f "$headers_file"' EXIT

base="http://localhost:${port}/fhir"

test "status" "$(curl -s --oauth2-bearer "$access_token" -D "$headers_file" -o /dev/null -w '%{response_code}' "$base")" "401"
test "WWW-Authenticate header" "$(www_authenticate "$headers_file")" "Bearer realm=\"$realm\", error=\"invalid_token\""
