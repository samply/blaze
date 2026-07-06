#!/bin/bash
set -euo pipefail

#
# This script creates a Patient coming from a unique source and verifies that
# the Patient search with _source finds exactly that Patient.
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

patient | create "$base/Patient" > /dev/null

bundle="$(search_strict "$base/Patient?_source=$source")"

test "total" "$(echo "$bundle" | jq -r .total)" "1"
test "source of all resources" "$(echo "$bundle" | jq -r '[.entry[].resource.meta.source] | unique | join(",")')" "$source"
