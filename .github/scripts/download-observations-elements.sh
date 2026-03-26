#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
num_patients=$(curl -sfH 'Accept: application/fhir+json' "$base/Patient?_summary=count" | jq -r .total)

num_codes=$(blazectl --server "$base" download Observation -q '_elements=subject&_count=1000' 2>/dev/null | jq -rc '.code // empty' | wc -l | xargs)
test "number of codes using GET" "$num_codes" "0"

num_subject_refs=$(blazectl --server "$base" download Observation -q '_elements=subject&_count=1000' 2>/dev/null | jq -rc '.subject.reference' | sort -u | wc -l | xargs)
test "number of unique subject refs using GET" "$num_subject_refs" "$num_patients"

num_codes=$(blazectl --server "$base" download Observation -p -q '_elements=subject&_count=1000' 2>/dev/null | jq -rc '.code // empty' | wc -l | xargs)
test "number of codes using POST" "$num_codes" "0"

num_subject_refs=$(blazectl --server "$base" download Observation -p -q '_elements=subject&_count=1000' 2>/dev/null | jq -rc '.subject.reference' | sort -u | wc -l | xargs)
test "number of unique subject refs using POST" "$num_subject_refs" "$num_patients"
