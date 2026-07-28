#!/bin/bash
set -euo pipefail

# Checks the `reject` failure mode of the external validator.
#
# In this mode a resource that fails external validation is not persisted.
# Instead the interaction is rejected with `400 Bad Request` and an
# OperationOutcome carrying the issues the external validator reported. This is
# asserted for the create and the update interaction and for a transaction
# bundle.
#
# The issue codes are the ones of the external validator (e.g. `invariant` or
# `structure`) and depend on the concrete validation finding, so only the
# severity is asserted here.
#
# A valid resource is used to show that conforming resources are still accepted
# and persisted.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
tag_system="https://blaze-server.org/fhir/CodeSystem/ValidationStatus"

request_body=$(mktemp)
response_body=$(mktemp)

trap 'rm -f "$request_body" "$response_body"' EXIT

# Sends the JSON in $request_body to the URL $2 using the HTTP method $1, writes
# the response body to $response_body and returns the response code.
send() {
  curl -s -X"$1" -o "$response_body" -w '%{response_code}' \
    -H 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' \
    -d @"$request_body" "$2"
}

# Asserts that the response of the rejected interaction $1 is an
# OperationOutcome with at least one issue of severity `error` or `fatal`.
test_rejection_outcome() {
  test "resourceType of the $1 response" "$(jq -r '.resourceType' "$response_body")" "OperationOutcome"
  test_not_equal "number of error issues of the $1 response" \
    "$(jq '[.issue[] | select(.severity == "error" or .severity == "fatal")] | length' "$response_body")" "0"

  echo "ℹ️ validation issues of the rejected $1:"
  jq -r '.issue[] | "  [\(.severity)] \(.code): \(.diagnostics // .details.text // "")"' "$response_body"
}

# --- a valid resource is accepted and persisted -----------------------------

jq '.id = "valid-patient"' "$script_dir/valid-patient.json" > "$request_body"

test "update status of the valid Patient" "$(send PUT "$base/Patient/valid-patient")" "201"
test "read status of the valid Patient" \
  "$(curl -s -o /dev/null -w '%{response_code}' -H 'Accept: application/fhir+json' "$base/Patient/valid-patient")" "200"

# --- an invalid resource is rejected on create ------------------------------

cp "$script_dir/invalid-patient.json" "$request_body"

test "create status of the invalid Patient" "$(send POST "$base/Patient")" "400"
test_rejection_outcome "create"

# --- an invalid resource is rejected on update ------------------------------

jq '.id = "invalid-patient"' "$script_dir/invalid-patient.json" > "$request_body"

test "update status of the invalid Patient" "$(send PUT "$base/Patient/invalid-patient")" "400"
test_rejection_outcome "update"

# --- an invalid resource is rejected inside a transaction -------------------

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": $(cat "$script_dir/invalid-patient.json"),
      "request": { "method": "POST", "url": "Patient" }
    }
  ]
}
END
}

bundle > "$request_body"

test "transaction status of the invalid Patient" "$(send POST "$base")" "400"
test_rejection_outcome "transaction"

# --- no invalid resource was persisted --------------------------------------

test "number of persisted invalid Patients" \
  "$(search_strict "$base/Patient?_tag=${tag_system}|invalid&_summary=count" | jq -r '.total')" "0"
