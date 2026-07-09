#!/bin/bash
set -euo pipefail

#
# This script creates a Patient and a Group, each referenced by an Observation
# carrying the same unique code, and verifies that the system-wide search with
# _has finds exactly the Patient and the Group, ordered by resource type hash.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
code="code-$(uuidgen | tr '[:upper:]' '[:lower:]')"

patient() {
cat <<END
{
  "resourceType": "Patient"
}
END
}

group() {
cat <<END
{
  "resourceType": "Group",
  "type": "person",
  "actual": true
}
END
}

observation() {
cat <<END
{
  "resourceType": "Observation",
  "status": "final",
  "code": {
    "coding": [
      {
        "system": "http://acme.org/codes",
        "code": "$code"
      }
    ]
  },
  "subject": {
    "reference": "$1"
  }
}
END
}

patient_id="$(patient | create "$base/Patient" | jq -r .id)"
group_id="$(group | create "$base/Group" | jq -r .id)"
observation "Patient/$patient_id" | create "$base/Observation" > /dev/null
observation "Group/$group_id" | create "$base/Observation" > /dev/null

bundle="$(search_strict "$base?_has:Observation:subject:code=http://acme.org/codes|$code")"

test "total" "$(echo "$bundle" | jq -r .total)" "2"
test "resource types" "$(echo "$bundle" | jq -r '[.entry[].resource.resourceType] | join(",")')" "Patient,Group"
test "patient id" "$(echo "$bundle" | jq -r '.entry[0].resource.id')" "$patient_id"
test "group id" "$(echo "$bundle" | jq -r '.entry[1].resource.id')" "$group_id"
