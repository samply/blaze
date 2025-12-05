#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

location() {
cat <<END
{
  "resourceType": "Location",
  "name": "$1",
  "position": {
    "latitude": $2,
    "longitude": $3
  }
}
END
}

location "Leipzig" "51.3397" "12.3731" | create "$base/Location" >/dev/null
location "Jakarta" "-6.2" "106.8167" | create "$base/Location" >/dev/null

search() {
  curl -s -H "Content-Type: application/fhir+json" "$base/Location?near=$1&summary=count" | jq -r .total
}

test "Location 900km from Florence finds Leipzig" "$(search "43.77925|11.24626|900|km")" "1"
