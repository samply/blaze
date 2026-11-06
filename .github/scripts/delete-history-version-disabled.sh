#!/bin/bash
set -euo pipefail

#
# This script tests that the delete history version interaction is rejected
# while it isn't enabled, which is the default. It does the following:
#
#  * creates a patient and updates it to create a second version
#  * expects a DELETE of the first version to return 405
#  * expects the same DELETE in a batch to return an error entry with 405
#  * expects the same DELETE in a transaction to return 405
#  * expects both versions to still exist
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "id": "$1",
  "gender": "$2"
}
END
}

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "$1",
  "entry": [
    {
      "request": {
        "method": "DELETE",
        "url": "$2"
      }
    }
  ]
}
END
}

# Sends a DELETE of the version $2 of the patient $1 and returns the response
# body followed by the status code on its own line.
delete_history_version() {
  curl -XDELETE -sH "Accept: application/fhir+json" -w '\n%{response_code}' "$base/Patient/$1/_history/$2"
}

# Sends the bundle on stdin and returns the response body followed by the
# status code on its own line.
send_bundle() {
  curl -sH "Accept: application/fhir+json" -H "Content-Type: application/fhir+json" -d @- -w '\n%{response_code}' "$base"
}

vread() {
  curl -sH "Accept: application/fhir+json" -w '%{response_code}' -o /dev/null "$base/Patient/$1/_history/$2"
}

echo "▶️create patient"
patient_id=$(echo '{"resourceType": "Patient"}' | create "$base/Patient" | jq -r '.id')

echo "▶️update patient"
patient "$patient_id" "male" | update "$base/Patient/$patient_id" > /dev/null

history="$(fetch_instance_history "$base" Patient "$patient_id")"
test "instance history total" "$(echo "$history" | jq -r .total)" "2"
v1="$(echo "$history" | jq -r '.entry[1].resource.meta.versionId')"
url="Patient/$patient_id/_history/$v1"

echo "▶️delete history version $v1"
response="$(delete_history_version "$patient_id" "$v1")"
test "delete status code" "$(echo "$response" | tail -n 1)" "405"
test "delete outcome issue code" "$(echo "$response" | sed '$d' | jq -r '.issue[0].code')" "processing"

echo "▶️delete history version $v1 in a batch"
response="$(bundle "batch" "$url" | send_bundle)"
test "batch status code" "$(echo "$response" | tail -n 1)" "200"
test "batch entry status" "$(echo "$response" | sed '$d' | jq -r '.entry[0].response.status')" "405"

echo "▶️delete history version $v1 in a transaction"
response="$(bundle "transaction" "$url" | send_bundle)"
test "transaction status code" "$(echo "$response" | tail -n 1)" "405"
test "transaction outcome issue expression" "$(echo "$response" | sed '$d' | jq -r '.issue[0].expression[0]')" "Bundle.entry[0].request.method"

# expect both versions to still exist
test "instance history total" "$(fetch_instance_history "$base" Patient "$patient_id" | jq -r .total)" "2"
test "read first version status code" "$(vread "$patient_id" "$v1")" "200"
