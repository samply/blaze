#!/bin/bash

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
curl -s "$base/__admin/dbs/index/column-families" | jq -r '.[].name' | grep -q "patient-last-change-index"

test "exit code" "$?" "1"
