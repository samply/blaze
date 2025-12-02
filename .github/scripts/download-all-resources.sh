#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
expected_size_json=$(curl -sH 'Accept: application/fhir+json' "$base?_summary=count" | jq -r .total)
expected_size_xml=$(curl -sH 'Accept: application/fhir+xml' "$base?_summary=count" | xq -x /Bundle/total/@value)
actual_size=$(blazectl --server "$base" download 2>/dev/null | wc -l | xargs)

test "totals (JSON, XML) match" "$expected_size_json" "$expected_size_xml"
test "download size" "$actual_size" "$expected_size_json"
