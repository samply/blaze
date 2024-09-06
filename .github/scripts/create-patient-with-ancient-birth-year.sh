#!/bin/bash -e

#
# This script tries to create a patient with a birth year with a leading zero
# and tries to read it back.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "birthDate": "0962"
}
END
}

HEADERS=$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(patient)" -o /dev/null -D - "$BASE/Patient")
LOCATION_HEADER=$(echo "$HEADERS" | grep -i location | tr -d '\r')
PATIENT=$(curl -s -H 'Accept: application/fhir+json' "${LOCATION_HEADER:10}")

test "birth date" "$(echo "$PATIENT" | jq -r .birthDate)" "0962"
