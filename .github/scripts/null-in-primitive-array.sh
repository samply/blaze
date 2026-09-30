#!/bin/bash
set -euo pipefail

#
# This script posts patients with null elements in the primitive array `given`,
# which aren't matched by extended properties, and expects them to be rejected,
# both as single create interaction and inside a transaction.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# null value without extended properties
patient_null_value() {
cat <<END
{
  "resourceType": "Patient",
  "name": [
    {
      "given": ["given-120511", null]
    }
  ]
}
END
}

# null extended properties without value
patient_null_extended_properties() {
cat <<END
{
  "resourceType": "Patient",
  "name": [
    {
      "_given": [null, {"id": "id-120708"}]
    }
  ]
}
END
}

transaction() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": $1,
      "request": {
        "method": "POST",
        "url": "Patient"
      }
    }
  ]
}
END
}

# Posts $2 to $1 and checks that the response is an error on the null element
# at expression $3.
post_and_check() {
  local result
  result=$(curl -sH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d "$2" -w '\n%{http_code}' "$1")
  local body
  body=$(echo "$result" | head -n -1)

  test "status" "$(echo "$result" | tail -n 1)" "400"
  test "resource type" "$(echo "$body" | jq -r .resourceType)" "OperationOutcome"
  test "severity" "$(echo "$body" | jq -r '.issue[0].severity')" "error"
  test "code" "$(echo "$body" | jq -r '.issue[0].code')" "invariant"
  test "diagnostics" "$(echo "$body" | jq -r '.issue[0].diagnostics')" "Error on value null. Expected type is \`string\`."
  test "expression" "$(echo "$body" | jq -r '.issue[0].expression[0]')" "$3"
}

echo "create with null value"
post_and_check "$base/Patient" "$(patient_null_value)" "Patient.name[0].given[1]"

echo "create with null extended properties"
post_and_check "$base/Patient" "$(patient_null_extended_properties)" "Patient.name[0].given[0]"

echo "transaction with null value"
post_and_check "$base" "$(transaction "$(patient_null_value)")" "Bundle.entry[0].resource.name[0].given[1]"

echo "transaction with null extended properties"
post_and_check "$base" "$(transaction "$(patient_null_extended_properties)")" "Bundle.entry[0].resource.name[0].given[0]"
