#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
ETAG=$(curl -sH 'Accept: application/fhir+json' -D - -o /dev/null "$BASE/metadata" | grep -i etag | cut -d: -f2)
STATUS="$(echo "If-None-Match:$ETAG" | curl -sH 'Accept: application/fhir+json' -H @- -o /dev/null -w "%{http_code}" "$BASE/metadata")"

test "status code" "$STATUS" "304"
