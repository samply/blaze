#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
state="$(curl -s "$base/__admin/dbs/index/column-families/patient-last-change-index/state" | jq -r .type)"

test "state" "$state" "$1"
