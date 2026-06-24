#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
vs_url="http://example.com/fhir/ValueSet/$(uuidgen | tr '[:upper:]' '[:lower:]')"

valueset() {
cat <<END
{
  "resourceType": "ValueSet",
  "url" : "$vs_url",
  "version" : "$1",
  "status" : "draft"
}
END
}

create() {
  curl -sfH "Content-Type: application/fhir+json" -H 'Accept: application/fhir+json' -d @- -o /dev/null "$base/ValueSet"
}

valueset "1.2.3" | create
valueset "1.3.4" | create
valueset "2.1.6" | create

search() {
  curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" "$base/ValueSet?url$1=$vs_url$2&_summary=count" | jq -r .total
}

test "ValueSet below v1 count" "$(search ":below" "|1")" "2"
test "ValueSet below v2 count" "$(search ":below" "|2")" "1"
test "ValueSet below all count" "$(search ":below" "")" "3"
test "ValueSet below v1.2.3 count" "$(search ":below" "|1.2.3")" "1"
test "ValueSet exact v1.2.3 count" "$(search "" "|1.2.3")" "1"
test "ValueSet exact unversioned count" "$(search "" "")" "3"
