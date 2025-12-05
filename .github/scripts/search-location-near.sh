#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

location() {
cat <<END
{
  "resourceType": "Location",
  "name": "Leipzig",
  "position": {
    "latitude": 51.3397,
    "longitude": 12.3731
  }
}
END
}

create() {
  curl -s -f -H "Content-Type: application/fhir+json" -H 'Accept: application/fhir+json' -d @- -o /dev/null "$base/Location"
}

location | create

search() {
  curl -s -H "Content-Type: application/fhir+json" "$base/Location?near=$1&summary=count" | jq -r .total
}

test "Location 900km from Florence finds Leipzig" "$(search "43.77925|11.24626|900|km")" "1"
