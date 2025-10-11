#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
HASH="$1"
PATIENT_COUNT="$2"

BLOOM_FILTERS="$(curl -s "$BASE/__admin/cql/bloom-filters")"

echo "All Bloom filters:"
echo "$BLOOM_FILTERS" | jq -r '.[]'

test "patient count" "$(echo "$BLOOM_FILTERS" | jq -r ".[] | select(.hash == \"$HASH\") | .patientCount")" "$PATIENT_COUNT"
