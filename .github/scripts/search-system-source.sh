#!/bin/bash
set -euo pipefail

#
# This script creates a Patient and an Observation coming from the same unique
# source and verifies that the system-wide search with _source finds exactly
# both of them, ordered by resource type hash.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
source="http://acme.org/sources/source-$(uuidgen | tr '[:upper:]' '[:lower:]')"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "meta": {
    "source": "$source"
  }
}
END
}

observation() {
cat <<END
{
  "resourceType": "Observation",
  "meta": {
    "source": "$source"
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

bundle="$(search_strict "$base?_source=$source")"

test "total" "$(echo "$bundle" | jq -r .total)" "2"
test "resource types" "$(echo "$bundle" | jq -r '[.entry[].resource.resourceType] | join(",")')" "Observation,Patient"
test "source of all resources" "$(echo "$bundle" | jq -r '[.entry[].resource.meta.source] | unique | join(",")')" "$source"
