#!/bin/bash -e

#
# This script test that the complex data type Distance works as expected
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

echo "ℹ️ Distance as extension value"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "extension": [
    {
      "url": "foo",
      "valueDistance": {
        "value": 23,
        "unit": "km",
        "system": "http://unitsofmeasure.org",
        "code": "km"
      }
    }
  ]
}
END
}

response="$(patient | create "$base/Patient")"

test "distance value" "$(echo "$response" | jq -r .extension[0].valueDistance.value)" "23"
test "distance unit" "$(echo "$response" | jq -r .extension[0].valueDistance.unit)" "km"
