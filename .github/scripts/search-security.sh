#!/bin/bash
set -euo pipefail

#
# This script creates a Patient carrying a unique security label and verifies
# that the Patient search with _security finds exactly that Patient.
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

patient | create "$base/Patient" > /dev/null

bundle="$(search_strict "$base/Patient?_security=http://acme.org/codes|$code")"

test "total" "$(echo "$bundle" | jq -r .total)" "1"
test "security label of all resources" "$(echo "$bundle" | jq -r '[.entry[].resource.meta.security[].code] | unique | join(",")')" "$code"
