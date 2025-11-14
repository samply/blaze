#!/bin/bash

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
hash="$1"
patient_count="$2"

test "patient count" "$(curl -s "$base/__admin/cql/bloom-filters" | jq -r ".[] | select(.hash == \"$hash\") | .patientCount")" "$patient_count"
