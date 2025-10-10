#!/bin/bash -e

#
# This script test that the complex data type Duration works as expected
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

echo "ℹ️ Duration as polymorph data element in ActivityDefinition"

activity_definition() {
cat <<END
{
  "resourceType": "ActivityDefinition",
  "timingDuration": {
    "value": 23,
    "unit": "second",
    "system": "http://unitsofmeasure.org",
    "code": "s"
  }
}
END
}

response="$(activity_definition | create "$base/ActivityDefinition")"

test "duration value" "$(echo "$response" | jq -r .timingDuration.value)" "23"
test "duration unit" "$(echo "$response" | jq -r .timingDuration.unit)" "second"

echo "ℹ️ Duration as extension value"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "extension": [
    {
      "url": "foo",
      "valueDuration": {
        "value": 23,
        "unit": "second",
        "system": "http://unitsofmeasure.org",
        "code": "s"
      }
    }
  ]
}
END
}

response="$(patient | create "$base/Patient")"

test "duration value" "$(echo "$response" | jq -r .extension[0].valueDuration.value)" "23"
test "duration unit" "$(echo "$response" | jq -r .extension[0].valueDuration.unit)" "second"
