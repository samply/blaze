#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
CAPABILITY_STATEMENT=$(curl -sH 'Accept: application/fhir+json' "$BASE/metadata")

test "status" "$(echo "$CAPABILITY_STATEMENT" | jq -r .status)" "active"
test "kind" "$(echo "$CAPABILITY_STATEMENT" | jq -r .kind)" "instance"
test "software name" "$(echo "$CAPABILITY_STATEMENT" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$CAPABILITY_STATEMENT" | jq -r .implementation.url)" "http://localhost:8080/fhir"
test "FHIR version" "$(echo "$CAPABILITY_STATEMENT" | jq -r .fhirVersion)" "4.0.1"
test "format" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.format | join(",")')" "application/fhir+json,application/fhir+xml"

test "Operation Measure \$evaluate-measure Definition" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "Measure") .operation[] | select(.name == "evaluate-measure") | .definition')" "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
test "Operation Patient \$everything Definition" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "Patient") .operation[] | select(.name == "everything") | .definition')" "http://hl7.org/fhir/OperationDefinition/Patient-everything"
test "Operation Patient \$everything Documentation" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "Patient") .operation[] | select(.name == "everything") | .documentation')" "Returns all resources from the patient compartment of one concrete patient including the patient. Has a fix limit of 10,000 resources if paging isn't used. Paging is supported when the _count parameter is used. No other params are supported."
test "Operation ValueSet \$expand Definition" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "expand") | .definition')" "http://hl7.org/fhir/OperationDefinition/ValueSet-expand"
test "Operation ValueSet \$expand Documentation" "$(echo "$CAPABILITY_STATEMENT" | jq -r '.rest[0].resource[] | select(.type == "ValueSet") .operation[] | select(.name == "expand") | .documentation')" "The \$expand operation can be used to expand all codes of a ValueSet."
