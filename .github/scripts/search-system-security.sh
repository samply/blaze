#!/bin/bash
set -euo pipefail

#
# This script creates a Patient and an Observation carrying the same unique
# security label and verifies that the system-wide search with _security finds
# exactly both of them, ordered by resource type hash.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
code="code-$(uuidgen | tr '[:upper:]' '[:lower:]')"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "meta": {
    "security": [
      {
        "system": "http://acme.org/codes",
        "code": "$code"
      }
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
    "security": [
      {
        "system": "http://acme.org/codes",
        "code": "$code"
      }
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

bundle="$(search_strict "$base?_security=http://acme.org/codes|$code")"

test "total" "$(echo "$bundle" | jq -r .total)" "2"
test "resource types" "$(echo "$bundle" | jq -r '[.entry[].resource.resourceType] | join(",")')" "Observation,Patient"
test "security label of all resources" "$(echo "$bundle" | jq -r '[.entry[].resource.meta.security[].code] | unique | join(",")')" "$code"
