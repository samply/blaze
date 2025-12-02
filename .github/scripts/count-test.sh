#!/bin/bash -e

#
# This script test that the complex data type Count works as expected
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

echo "ℹ️ Count as extension value"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "extension": [
    {
      "url": "foo",
      "valueCount": {
        "value": 23,
        "system": "http://unitsofmeasure.org",
        "code": "1"
      }
    }
  ]
}
END
}

response="$(patient | create "$base/Patient")"

test "count value" "$(echo "$response" | jq -r .extension[0].valueCount.value)" "23"
test "count code" "$(echo "$response" | jq -r .extension[0].valueCount.code)" "1"
