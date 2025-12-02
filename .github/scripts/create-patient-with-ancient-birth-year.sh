#!/bin/bash -e

#
# This script tries to create a patient with a birth year with a leading zero
# and tries to read it back.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

gen_patient() {
cat <<END
{
  "resourceType": "Patient",
  "birthDate": "0962"
}
END
}

headers=$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(gen_patient)" -o /dev/null -D - "$base/Patient")
location_header=$(echo "$headers" | grep -i location | tr -d '\r')
patient=$(curl -s -H 'Accept: application/fhir+json' "${location_header:10}")

test "birth date" "$(echo "$patient" | jq -r .birthDate)" "0962"
