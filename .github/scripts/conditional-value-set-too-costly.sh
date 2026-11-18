#!/bin/bash
set -euo pipefail

# Tests that the value sets of a condition of a conditional create or delete
# can expand to at most 10,000 codes, because the expanded codes are stored in
# the transaction log.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
id="$(uuidgen | tr '[:upper:]' '[:lower:]')"

# Creates a CodeSystem with $1 concepts and a ValueSet including all of them.
# Returns the URL of the ValueSet.
create_value_set() {
  local cs_url="http://example.com/fhir/CodeSystem/$id-$1"
  local vs_url="http://example.com/fhir/ValueSet/$id-$1"

  jq -n --arg url "$cs_url" --argjson n "$1" '{
    resourceType: "CodeSystem",
    url: $url,
    status: "active",
    content: "complete",
    concept: [range($n) | {code: "code-\(.)"}]
  }' | create "$base/CodeSystem" > /dev/null

  jq -n --arg url "$vs_url" --arg cs_url "$cs_url" '{
    resourceType: "ValueSet",
    url: $url,
    status: "active",
    compose: {include: [{system: $cs_url}]}
  }' | create "$base/ValueSet" > /dev/null

  echo "$vs_url"
}

vs_url_10000="$(create_value_set 10000)"
vs_url_10001="$(create_value_set 10001)"

echo "{\"resourceType\": \"Patient\", \"id\": \"$id\"}" | update "$base/Patient/$id" > /dev/null

condition() {
cat <<END
{
  "resourceType": "Condition",
  "subject": {"reference": "Patient/$id"}
}
END
}

expected_diagnostics="The value sets of the condition expand to 10,001 codes, which is more than the limit of 10,000 codes."

test_too_costly() {
  test "$1 status" "$(echo "$2" | jq -r .status)" "409"
  test "$1 resource type" "$(echo "$2" | jq -r .body.resourceType)" "OperationOutcome"
  test "$1 code" "$(echo "$2" | jq -r .body.issue[0].code)" "too-costly"
  test "$1 diagnostics" "$(echo "$2" | jq -r .body.issue[0].diagnostics)" "$expected_diagnostics"
}

# Returns the status and the body of the response to the curl args as JSON.
request() {
  local body_file
  body_file="$(mktemp)"
  local status
  status="$(curl -sS -o "$body_file" -w '%{http_code}' -H 'Accept: application/fhir+json' "$@")"
  jq -c --arg status "$status" '{status: $status, body: .}' "$body_file"
  rm "$body_file"
}

response="$(condition | request -H 'Content-Type: application/fhir+json' \
  -H "If-None-Exist: code:in=$vs_url_10000" -d @- "$base/Condition")"
test "conditional create with 10,000 codes status" "$(echo "$response" | jq -r .status)" "201"

response="$(condition | request -H 'Content-Type: application/fhir+json' \
  -H "If-None-Exist: code:in=$vs_url_10001" -d @- "$base/Condition")"
test_too_costly "conditional create" "$response"

response="$(request -XDELETE -H 'Prefer: return=OperationOutcome' \
  "$base/Condition?code:in=$vs_url_10001")"
test_too_costly "conditional delete" "$response"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "$1",
  "entry": [
    {
      "resource": $(condition),
      "request": {
        "method": "POST",
        "url": "Condition",
        "ifNoneExist": "code:in=$vs_url_10001"
      }
    }
  ]
}
END
}

response="$(bundle transaction | request -H 'Content-Type: application/fhir+json' -d @- "$base")"
test_too_costly "transaction" "$response"

response="$(bundle batch | request -H 'Content-Type: application/fhir+json' -d @- "$base")"
test "batch status" "$(echo "$response" | jq -r .status)" "200"
entry_response="$(echo "$response" | jq -c '{status: .body.entry[0].response.status, body: .body.entry[0].response.outcome}')"
test_too_costly "batch entry" "$entry_response"
