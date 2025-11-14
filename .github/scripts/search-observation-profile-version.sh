#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
profile_url="http://example.com/fhir/StructureDefinition/$(uuidgen | tr '[:upper:]' '[:lower:]')"

observation() {
cat <<END
{
  "resourceType": "Observation",
  "meta" : {
    "profile" : [
      "$profile_url|$1"
    ]
  }
}
END
}

create() {
  curl -s -f -H "Content-Type: application/fhir+json" -H 'Accept: application/fhir+json' -d @- -o /dev/null "$base/Observation"
}

observation "1.2.3" | create
observation "1.3.4" | create
observation "2.1.6" | create

search() {
  curl -s -H "Content-Type: application/fhir+json" "$base/Observation?_profile$1=$profile_url$2&_summary=count" | jq -r .total
}

test "Observation below v1 count" "$(search ":below" "|1")" "2"
test "Observation below v2 count" "$(search ":below" "|2")" "1"
test "Observation below all count" "$(search ":below" "")" "3"
# Patch version doesn't work with below
test "Observation below v1.2.3 count" "$(search ":below" "|1.2.3")" "0"
test "Observation exact v1.2.3 count" "$(search "" "|1.2.3")" "1"
