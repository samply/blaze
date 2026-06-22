#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

search() {
  curl -sH "Prefer: handling=$1" -H "Accept: application/fhir+json" "$base/$2?$3"
}

echo "For implemented modifier"
response="$(search lenient "Observation" "subject:Patient=foo")"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
response="$(search lenient "Observation" "_profile:below=http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab&_summary=count")"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
test "count" "$(echo "$response" | jq -r .total)" "27218"

echo
echo "below modifier on non-canonical uri"
echo "# In strict mode"
response="$(search strict "Procedure" 'instantiates-uri:below=http://example.com')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "OperationOutcome"
test "issue code" "$(echo "$response" | jq -r '.issue[0].code')" "not-supported"
test "diagnostics" "$(echo "$response" | jq -r '.issue[0].diagnostics')" "Unsupported modifier \`below\` on search parameter \`instantiates-uri\`."

echo "# In lenient mode"
response="$(search lenient "Procedure" 'instantiates-uri:below=http://example.com')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
test "param is ignored" "$(echo "$response" | jq -r '.link[] | select(.relation == "self") | .url | contains("instantiates-uri") | not')" "true"

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
test "param is ignored" "$(echo "$response" | jq -r '.link[] | select(.relation == "self") | .url | contains("name") | not')" "true"

echo "with unknown modifier"
response="$(search lenient "Patient" 'name:unknown=foo')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "Bundle"
test "param is ignored" "$(echo "$response" | jq -r '.link[] | select(.relation == "self") | .url | contains("name") | not')" "true"

echo "token in modifier without terminology service"
response="$(search strict "Condition" 'code:in=http://fhir.org/VCL?v1=(http://snomed.info/sct)concept<<73211009')"
test "response type" "$(echo "$response" | jq -r .resourceType)" "OperationOutcome"
test "issue code" "$(echo "$response" | jq -r '.issue[0].code')" "exception"
test "diagnostics" "$(echo "$response" | jq -r '.issue[0].diagnostics')" "Error while expanding the ValueSet \`http://fhir.org/VCL?v1=(http://snomed.info/sct)concept<<73211009\`. Cause: Terminology operations are not supported. Please enable either the external or the internal terminology service."
