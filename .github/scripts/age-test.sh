#!/bin/bash -e

#
# This script test that the complex data type Age works as expected
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

echo "ℹ️ Age as polymorph data element in Condition"

condition() {
cat <<END
{
  "resourceType": "Condition",
  "onsetAge": {
    "value": 42,
    "unit": "year",
    "system": "http://unitsofmeasure.org",
    "code": "a"
  }
}
END
}

response="$(condition | create "$base/Condition")"

test "age value" "$(echo "$response" | jq -r .onsetAge.value)" "42"
test "age unit" "$(echo "$response" | jq -r .onsetAge.unit)" "year"

echo "ℹ️ Age as extension value"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "extension": [
    {
      "url": "foo",
      "valueAge": {
        "value": 42,
        "unit": "year",
        "system": "http://unitsofmeasure.org",
        "code": "a"
      }
    }
  ]
}
END
}

response="$(patient | create "$base/Patient")"

test "age value" "$(echo "$response" | jq -r .extension[0].valueAge.value)" "42"
test "age unit" "$(echo "$response" | jq -r .extension[0].valueAge.unit)" "year"
