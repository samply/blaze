#!/bin/bash
set -euo pipefail

#
# This script creates a Patient and verifies that the type-level search with
# _id finds exactly this Patient.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

patient_id="$(echo '{"resourceType": "Patient"}' | create "$base/Patient" | jq -r .id)"

bundle="$(search_strict "$base/Patient?_id=$patient_id")"

test "total" "$(echo "$bundle" | jq -r .total)" "1"
test "resource type" "$(echo "$bundle" | jq -r '.entry[0].resource.resourceType')" "Patient"
test "patient id" "$(echo "$bundle" | jq -r '.entry[0].resource.id')" "$patient_id"
