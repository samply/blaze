#!/bin/bash
set -euo pipefail

#
# This script creates a Patient and an Observation claiming to conform to the
# same unique profile and verifies that the system-wide search with _profile
# finds exactly both of them, ordered by resource type hash.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
profile="http://acme.org/profiles/profile-$(uuidgen | tr '[:upper:]' '[:lower:]')"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "meta": {
    "profile": [
      "$profile"
    ]
  }
}
END
}

observation() {
cat <<END
{
  "resourceType": "Observation",
  "meta": {
    "profile": [
      "$profile"
    ]
  },
  "status": "final",
  "code": {
    "text": "test"
  }
}
END
}

patient | create "$base/Patient" > /dev/null
observation | create "$base/Observation" > /dev/null

bundle="$(search_strict "$base?_profile=$profile")"

test "total" "$(echo "$bundle" | jq -r .total)" "2"
test "resource types" "$(echo "$bundle" | jq -r '[.entry[].resource.resourceType] | join(",")')" "Observation,Patient"
test "profile of all resources" "$(echo "$bundle" | jq -r '[.entry[].resource.meta.profile[]] | unique | join(",")')" "$profile"
