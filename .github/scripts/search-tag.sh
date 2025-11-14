#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

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

curl -s -f -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(patient)" -o /dev/null "$base/Patient"

bundle="$(curl -sH 'Accept: application/fhir+json' -H 'Prefer: handling=strict' "$base/Patient?_tag=http://acme.org/codes|needs-review")"

test "tag of all resources" "$(echo "$bundle" | jq -r '.entry[].resource.meta.tag[].code' | uniq)" "needs-review"
