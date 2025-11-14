#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
etag=$(curl -sH 'Accept: application/fhir+json' -D - -o /dev/null "$base/metadata" | grep -i etag | cut -d: -f2)
status="$(echo "If-None-Match:$etag" | curl -sH 'Accept: application/fhir+json' -H @- -o /dev/null -w "%{http_code}" "$base/metadata")"

test "status code" "$status" "304"
