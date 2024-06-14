#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "meta": {
    "tag": [
      {
        "system": "http://acme.org/codes",
        "code": "needs-review"
      }
    ]
  }
}
END
}

curl -s -f -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(patient)" -o /dev/null "$BASE/Patient"

BUNDLE="$(curl -sH 'Accept: application/fhir+json' -H 'Prefer: handling=strict' "$BASE/Patient?_tag=http://acme.org/codes|needs-review")"

test "tag of all resources" "$(echo "$BUNDLE" | jq -r '.entry[].resource.meta.tag[].code' | uniq)" "needs-review"
