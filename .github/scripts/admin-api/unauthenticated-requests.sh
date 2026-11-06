#!/bin/bash
set -euo pipefail

# Checks that every endpoint of the Admin API rejects unauthenticated requests
# with 401 and a body matching the format of the endpoint.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

base="http://localhost:8080"

access_token=$(curl -sSfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)
test_non_empty "access token" "$access_token"

fill_path_params() {
  sed -e 's/{db}/index/g' -e 's/{column-family}/default/g' -e 's/{id}/AAAAAAAAAAAAAAAA/g'
}

# the OpenAPI spec lists all endpoints of the Admin API except itself
endpoints=$(curl -sSfH 'Accept: application/json' --oauth2-bearer "$access_token" "$base/fhir/__admin/openapi.json" \
  | jq -r '.paths | to_entries[] | .key as $path | .value | keys[] | "\(.) \($path)"' \
  | fill_path_params)
endpoints="$endpoints
get /fhir/__admin/openapi.json
get /fhir/__admin/unknown"

test_le "minimum number of endpoints" 15 "$(echo "$endpoints" | wc -l)"

body_file=$(mktemp)
trap 'rm -f "$body_file"' EXIT

while read -r method path; do
  if [[ "$path" == *"{"* ]]; then
    echo "🆘 unknown path param in $path"
    exit 1
  fi

  echo "${method^^} $path"
  response=$(curl -s -X "${method^^}" -o "$body_file" -w '%{response_code} %{content_type}' "$base$path")
  test "status" "${response%% *}" "401"

  if [[ "$path" == "/fhir/__admin/Task"* ]]; then
    test "content type" "${response#* }" "application/fhir+json;charset=utf-8"
    test "message code" "$(jq -r '.issue[0].details.coding[0].code' "$body_file")" "MSG_AUTH_REQUIRED"
  else
    test "content type" "${response#* }" "application/json;charset=utf-8"
    test "message" "$(jq -r .msg "$body_file")" "Authentication required."
  fi
done <<< "$endpoints"

test "status of authenticated request" "$(curl -s --oauth2-bearer "$access_token" -o /dev/null -w '%{response_code}' "$base/fhir/__admin/dbs")" "200"
