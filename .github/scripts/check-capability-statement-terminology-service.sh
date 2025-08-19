#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
CAPABILITY_STATEMENT=$(curl -sH 'Accept: application/fhir+json' "$BASE/metadata")

test "status" "$(echo "$CAPABILITY_STATEMENT" | jq -r .status)" "active"
test "kind" "$(echo "$CAPABILITY_STATEMENT" | jq -r .kind)" "instance"
test "software name" "$(echo "$CAPABILITY_STATEMENT" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$CAPABILITY_STATEMENT" | jq -r .implementation.url)" "http://localhost:8080/fhir"
test "FHIR version" "$(echo "$CAPABILITY_STATEMENT" | jq -r .fhirVersion)" "6.0.0-ballot3"
test "format" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.format | join(",")')" "application/fhir+json,application/fhir+xml"

test "Operation CodeSystem \$validate-code Definition" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "CodeSystem") .operation[] | select(.name == "validate-code") | .definition')" "http://hl7.org/fhir/OperationDefinition/CodeSystem-validate-code"
test "Operation CodeSystem \$validate-code Documentation" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "CodeSystem") .operation[] | select(.name == "validate-code") | .documentation')" "Validate that a coded value is in the code system."
test "Operation ValueSet \$expand Definition" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "expand") | .definition')" "http://hl7.org/fhir/OperationDefinition/ValueSet-expand"
test "Operation ValueSet \$expand Documentation" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "expand") | .documentation')" "The \$expand operation can be used to expand all codes of a ValueSet."
test "Operation ValueSet \$validate-code Definition" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "validate-code") | .definition')" "http://hl7.org/fhir/OperationDefinition/ValueSet-validate-code"
test "Operation ValueSet \$validate-code Documentation" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "validate-code") | .documentation')" "Validate that a coded value is in the set of codes allowed by a value set."
