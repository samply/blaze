#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

search() {
  curl -sH "Prefer: handling=$1" -H "Accept: application/fhir+json" "$base/$2?$3"
}

echo "For implemented modifier"
response="$(search lenient "Observation" "subject:Patient=foo")"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
response="$(search lenient "Observation" "_profile:below=http://example.com/fhir/StructureDefinition/f6f12c69-9431-492d-a3a5-469d36f5e104")"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"

echo
echo "# In strict mode"
echo "with modifier not implemented"
response="$(search strict "Patient" 'name:exact=foo')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "OperationOutcome"
test "issue code" "$(echo "$response" | jq -r '.issue[0].code')" "not-supported"

echo "with unknown modifier"
response="$(search strict "Patient" 'name:unknown=foo')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "OperationOutcome"
test "issue code" "$(echo "$response" | jq -r '.issue[0].code')" "invalid"

echo
echo "# In lenient mode"
echo "with modifier not implemented"
response="$(search lenient "Patient" 'name:exact=foo')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
test "param is ignored" "$(echo "$response" | jq -r '.link[0].url | contains("name") | not')" "true"

echo "with unknown modifier"
response="$(search lenient "Patient" 'name:unknown=foo')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
test "param is ignored" "$(echo "$response" | jq -r '.link[0].url | contains("name") | not')" "true"
