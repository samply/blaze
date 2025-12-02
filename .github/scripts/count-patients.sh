#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1
query="${2//[[:space:]]/}"
expected_size=$3
actual_size=$(blazectl --server "$base" download "$type" -q "$query" 2>/dev/null | jq -r .subject.reference | sort -u | wc -l | xargs)

test "number of patients" "$actual_size" "$expected_size"
