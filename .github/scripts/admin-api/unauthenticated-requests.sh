#!/bin/bash
set -euo pipefail

# Checks that every endpoint of the Admin API rejects unauthenticated requests
# with 401 and a body matching the format of the endpoint.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

base="http://localhost:8080"

access_token=$(curl -sSfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)
test_non_empty "access token" "$access_token"

# all endpoints of the Admin API, the unit test `blaze.admin-api-test/unauthenticated-test`
# covers every route of the router
endpoints=$(cat <<'EOF'
get /fhir/__admin
get /fhir/__admin/dbs
get /fhir/__admin/dbs/index/stats
get /fhir/__admin/dbs/index/column-families
get /fhir/__admin/dbs/index/column-families/default/state
get /fhir/__admin/dbs/index/column-families/default/metadata
get /fhir/__admin/dbs/index/column-families/default/tables
get /fhir/__admin/Task
post /fhir/__admin/Task
get /fhir/__admin/Task/AAAAAAAAAAAAAAAA
get /fhir/__admin/Task/AAAAAAAAAAAAAAAA/_history
post /fhir/__admin/Task/AAAAAAAAAAAAAAAA/$pause
post /fhir/__admin/Task/AAAAAAAAAAAAAAAA/$resume
post /fhir/__admin/Task/AAAAAAAAAAAAAAAA/$cancel
get /fhir/__admin/cql/cache-stats
get /fhir/__admin/cql/bloom-filters
get /fhir/__admin/unknown
EOF
)

realm="http://localhost:8090/realms/blaze"

body_file=$(mktemp)
headers_file=$(mktemp)
trap 'rm -f "$body_file" "$headers_file"' EXIT

while read -r method path; do
  echo "${method^^} $path"
  response=$(curl -s -X "${method^^}" -D "$headers_file" -o "$body_file" -w '%{response_code} %{content_type}' "$base$path")
  test "status" "${response%% *}" "401"
  test "WWW-Authenticate header" "$(www_authenticate "$headers_file")" "Bearer realm=\"$realm\""

  if [[ "$path" == "/fhir/__admin/Task"* ]]; then
    test "content type" "${response#* }" "application/fhir+json;charset=utf-8"
    test "message code" "$(jq -r '.issue[0].details.coding[0].code' "$body_file")" "MSG_AUTH_REQUIRED"
  else
    test "content type" "${response#* }" "application/json;charset=utf-8"
    test "message" "$(jq -r .msg "$body_file")" "Authentication required."
  fi
done <<< "$endpoints"

echo "GET /fhir/__admin/dbs with a rejected token"
test "status" "$(curl -s --oauth2-bearer "invalid" -D "$headers_file" -o /dev/null -w '%{response_code}' "$base/fhir/__admin/dbs")" "401"
test "WWW-Authenticate header" "$(www_authenticate "$headers_file")" "Bearer realm=\"$realm\", error=\"invalid_token\""

test "status of authenticated request" "$(curl -s --oauth2-bearer "$access_token" -o /dev/null -w '%{response_code}' "$base/fhir/__admin/dbs")" "200"
