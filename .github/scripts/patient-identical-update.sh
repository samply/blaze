#!/bin/bash -e

#
# This script tests that an update without changes of the resource content
# doesn't create a new history entry.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": $1,
      "request": {
        "method": "PUT",
        "url": "Patient/$2"
      }
    }
  ]
}
END
}

base="http://localhost:8080/fhir"
patient_identifier="X79746011X"
patient=$(curl -sH "Accept: application/fhir+json" "$base/Patient?identifier=$patient_identifier" | jq -cM '.entry[0].resource')
id="$(echo "$patient" | jq -r .id)"
version_id="$(echo "$patient" | jq -r .meta.versionId)"

# Update Interaction
result=$(curl -sXPUT -H "Content-Type: application/fhir+json" -d "$patient" "$base/Patient/$id")
result_version_id="$(echo "$result" | jq -r .meta.versionId)"

test "update versionId" "$result_version_id" "$version_id"

# Transaction Interaction
result=$(curl -sH "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d "$(bundle "$patient" "$id")" "$base")
result_version_id="$(echo "$result" | jq -r '.entry[0].resource.meta.versionId')"

test "transaction versionId" "$result_version_id" "$version_id"

history_total=$(curl -sH "Accept: application/fhir+json" "$base/Patient/$id/_history" | jq -r '.total')

test "history total" "$history_total" "1"
