#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
NUM_PATIENTS=$(curl -s "$BASE/Patient?_summary=count" | jq -r .total)

NUM_CODES=$(blazectl --server "$BASE" download Observation -q '_elements=subject' 2>/dev/null | jq -rc '.code' | grep -cv null | xargs)
test "number of codes using GET" "$NUM_CODES" "0"

NUM_SUBJECT_REFS=$(blazectl --server "$BASE" download Observation -q '_elements=subject' 2>/dev/null | jq -rc '.subject.reference' | sort -u | wc -l | xargs)
test "number of unique subject refs using GET" "$NUM_SUBJECT_REFS" "$NUM_PATIENTS"

NUM_CODES=$(blazectl --server "$BASE" download Observation -p -q '_elements=subject' 2>/dev/null | jq -rc '.code' | grep -cv null | xargs)
test "number of codes using POST" "$NUM_CODES" "0"

NUM_SUBJECT_REFS=$(blazectl --server "$BASE" download Observation -p -q '_elements=subject' 2>/dev/null | jq -rc '.subject.reference' | sort -u | wc -l | xargs)
test "number of unique subject refs using POST" "$NUM_SUBJECT_REFS" "$NUM_PATIENTS"
