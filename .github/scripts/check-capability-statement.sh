#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="${1:-http://localhost:8080/fhir}"

echo "ℹ️ JSON Format"

capability_statement=$(curl -sfH 'Accept: application/fhir+json' "$base/metadata")

test "status" "$(echo "$capability_statement" | jq -r .status)" "active"
test "kind" "$(echo "$capability_statement" | jq -r .kind)" "instance"
test "software name" "$(echo "$capability_statement" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$capability_statement" | jq -r .implementation.url)" "http://localhost:8080/fhir"
test "FHIR version" "$(echo "$capability_statement" | jq -r .fhirVersion)" "4.0.1"
test "format" "$(echo "$capability_statement" | jq -r '.format | join(",")')" "application/fhir+json,application/fhir+xml"

test "Patient Profile" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Patient") .profile')" "http://hl7.org/fhir/StructureDefinition/Patient"

test "Operation Measure \$evaluate-measure Definition" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Measure") .operation[] | select(.name == "evaluate-measure") | .definition')" "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
test "Operation Patient \$everything Definition" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Patient") .operation[] | select(.name == "everything") | .definition')" "http://hl7.org/fhir/OperationDefinition/Patient-everything"
test "Operation Patient \$everything Documentation" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Patient") .operation[] | select(.name == "everything") | .documentation')" "Returns all resources from the patient compartment of one concrete patient including the patient. Has a fix limit of 10,000 resources if paging isn't used. Paging is supported when the _count parameter is used. No other params are supported."

test "Operation \$totals Documentation" "$(echo "$capability_statement" | jq -r '.rest[0].operation[] | select(.name == "totals") | .documentation')" "Retrieves the total counts of resources available by resource type."

echo "ℹ️ XML Format"

capability_statement=$(curl -sfH 'Accept: application/fhir+xml' "$base/metadata")

test "status" "$(echo "$capability_statement" | xq -x //status/@value)" "active"
test "kind" "$(echo "$capability_statement" | xq -x //kind/@value)" "instance"
test "software name" "$(echo "$capability_statement" | xq -x //software/name/@value)" "Blaze"
test "URL" "$(echo "$capability_statement" | xq -x //implementation/url/@value)" "http://localhost:8080/fhir"
test "FHIR version" "$(echo "$capability_statement" | xq -x //fhirVersion/@value)" "4.0.1"
