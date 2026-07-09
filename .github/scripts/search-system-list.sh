#!/bin/bash
set -euo pipefail

#
# This script creates a Patient, an Observation and a List referencing both and
# verifies that the system-wide search with _list finds exactly the two
# referenced resources, ordered by resource type hash.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient"
}
END
}

observation() {
cat <<END
{
  "resourceType": "Observation",
  "status": "final",
  "code": {
    "text": "test"
  }
}
END
}

list() {
cat <<END
{
  "resourceType": "List",
  "status": "current",
  "mode": "working",
  "entry": [
    {
      "item": {
        "reference": "Patient/$1"
      }
    },
    {
      "item": {
        "reference": "Observation/$2"
      }
    }
  ]
}
END
}

patient_id="$(patient | create "$base/Patient" | jq -r .id)"
observation_id="$(observation | create "$base/Observation" | jq -r .id)"
list_id="$(list "$patient_id" "$observation_id" | create "$base/List" | jq -r .id)"

bundle="$(search_strict "$base?_list=$list_id")"

test "total" "$(echo "$bundle" | jq -r .total)" "2"
test "resource types" "$(echo "$bundle" | jq -r '[.entry[].resource.resourceType] | join(",")')" "Observation,Patient"
test "observation id" "$(echo "$bundle" | jq -r '.entry[0].resource.id')" "$observation_id"
test "patient id" "$(echo "$bundle" | jq -r '.entry[1].resource.id')" "$patient_id"
