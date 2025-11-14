#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
observation_id=$(uuidgen | tr '[:upper:]' '[:lower:]') 

curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$patient_id\"}" -o /dev/null "$base/Patient/$patient_id"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Observation\", \"id\": \"$observation_id\", \"subject\": {\"reference\": \"Patient/$patient_id\"}}" -o /dev/null "$base/Observation/$observation_id"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "DELETE",
        "url": "Patient/$patient_id"
      }
    },
    {
      "request": {
        "method": "DELETE",
        "url": "Observation/$observation_id"
      }
    }
  ]
}
END
}
response=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$base")

test "delete response for the first entry" "$(echo "$response" | jq -r '.entry[0].response.status')" "204"
test "delete response for the second entry" "$(echo "$response" | jq -r '.entry[1].response.status')" "204"
