#!/bin/bash -e

#
# This script fetches the first system bundle, adds a new Patient and expects
# the total on the next page being still the same
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

FIRST_PAGE="$(curl -sH "Accept: application/fhir+json" "$BASE")"
TOTAL="$(echo "$FIRST_PAGE" | jq -r .total)"
SELF_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "self") | .url')"
NEXT_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"

# create new patient
curl -sfH 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Patient\"}" -o /dev/null "$BASE/Patient"

FIRST_PAGE="$(curl -sH "Accept: application/fhir+json" "$SELF_LINK")"
SECOND_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"

test "first page total" "$(echo "$FIRST_PAGE" | jq -r .total)" "$TOTAL"
test "second page total" "$(echo "$SECOND_PAGE" | jq -r .total)" "$TOTAL"
