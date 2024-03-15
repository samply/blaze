#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
FIRST_PAGE=$(curl -s "$BASE/_history")
FIRST_PAGE_SIZE=$(echo "$FIRST_PAGE" | jq -r '.entry | length')
NEXT_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"
SECOND_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"
SECOND_PAGE_SIZE=$(echo "$SECOND_PAGE" | jq -r '.entry | length')

test "first page size" "$FIRST_PAGE_SIZE" "50"
test "second page size" "$SECOND_PAGE_SIZE" "50"
