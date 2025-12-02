#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
capability_statement=$(curl -sH 'Accept: application/fhir+json' "$base/metadata")

test "status" "$(echo "$capability_statement" | jq -r .status)" "active"
test "kind" "$(echo "$capability_statement" | jq -r .kind)" "instance"
test "software name" "$(echo "$capability_statement" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$capability_statement" | jq -r .implementation.url)" "http://localhost:8080/fhir"
test "FHIR version" "$(echo "$capability_statement" | jq -r .fhirVersion)" "4.0.1"
test "format" "$(echo "$capability_statement" | jq -r '.format | join(",")')" "application/fhir+json,application/fhir+xml"

test "Operation CodeSystem \$validate-code Definition" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "CodeSystem") .operation[] | select(.name == "validate-code") | .definition')" "http://hl7.org/fhir/OperationDefinition/CodeSystem-validate-code"
test "Operation CodeSystem \$validate-code Documentation" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "CodeSystem") .operation[] | select(.name == "validate-code") | .documentation')" "Validate that a coded value is in the code system."
test "Operation ValueSet \$expand Definition" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "expand") | .definition')" "http://hl7.org/fhir/OperationDefinition/ValueSet-expand"
test "Operation ValueSet \$expand Documentation" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "expand") | .documentation')" "The \$expand operation can be used to expand all codes of a ValueSet."
test "Operation ValueSet \$validate-code Definition" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "validate-code") | .definition')" "http://hl7.org/fhir/OperationDefinition/ValueSet-validate-code"
test "Operation ValueSet \$validate-code Documentation" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "validate-code") | .documentation')" "Validate that a coded value is in the set of codes allowed by a value set."
