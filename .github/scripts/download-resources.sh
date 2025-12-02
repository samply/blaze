#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1
expected_size=$(curl -s "$base/${type}?_summary=count" | jq -r .total)
actual_size=$(blazectl --server "$base" download "$type" 2>/dev/null | wc -l | xargs)

test "download size" "$actual_size" "$expected_size"
