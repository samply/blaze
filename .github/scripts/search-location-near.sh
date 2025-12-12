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

response="$(curl -s -H "Content-Type: application/fhir+json" "$base/Location?near=43.77925|11.24626|900|km")"

test "Location 900km from Florence finds Leipzig" "$(echo "$response" | jq -r .total)" "1"

match_extension="$(echo "$response" | jq '.entry[0] | .search.extension[0]')"
if echo "$match_extension" | jq -er '.valueDistance.unit == "m"' >/dev/null \
  && echo "$match_extension" | jq -er '.url == "http://hl7.org/fhir/StructureDefinition/location-distance"' >/dev/null; then
  echo "✅ the search result contains a location-distance extension"
else
  echo "🆘 the search does not contain a location-distance extension"
  exit 1
fi
