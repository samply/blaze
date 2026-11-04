#!/bin/bash
set -euo pipefail

#
# This script tests the delete history version interaction, sending each DELETE
# in the way given by the first argument: `direct`, `batch` or `transaction`.
#
# It does the following:
#
#  * creates a patient
#  * updates that patient twice to create a second and third version
#  * expects the history to contain three entries
#  * expects deleting the current version to fail with 409
#  * deletes the second version of the patient
#  * expects the history to contain only the first and the current version
#  * expects a versioned read of the deleted version to return 404
#  * expects the type history doesn't contain the deleted version
#  * expects the system history doesn't contain the deleted version
#  * expects deleting the deleted version again to return 204
#  * expects deleting a valid version id that isn't a version number to return 204
#  * expects deleting an invalid version id to return 400
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
mode="${1:-}"

case "$mode" in
  direct|batch|transaction) ;;
  *)
    echo "Usage: $0 direct|batch|transaction" >&2
    exit 2
    ;;
esac

patient() {
cat <<END
{
  "resourceType": "Patient",
  "id": "$1",
  "gender": "$2"
}
END
}

delete_bundle() {
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

# Deletes the version $2 of the patient $1 in the way given by `mode` and returns
# the status of that DELETE. In a bundle, that's the status of its entry, unless
# the whole bundle failed, as a transaction does on errors.
delete_history_version() {
  echo "▶️delete history version $2 ($mode)" >&2
  local url="Patient/$1/_history/$2"
  if [ "$mode" = "direct" ]; then
    curl -XDELETE -s -w '%{response_code}' -o /dev/null "$base/$url"
  else
    local response status
    response="$(delete_bundle "$mode" "$url" | curl -sH "Accept: application/fhir+json" -H "Content-Type: application/fhir+json" -d @- -w '\n%{response_code}' "$base")"
    status="$(echo "$response" | tail -n 1)"
    if [ "$status" = "200" ]; then
      echo "$response" | sed '$d' | jq -r '.entry[0].response.status'
    else
      echo "$status"
    fi
  fi
}

vread() {
  curl -sH "Accept: application/fhir+json" -w '%{response_code}' -o /dev/null "$base/Patient/$1/_history/$2"
}

echo "▶️create patient"
patient_id=$(echo '{"resourceType": "Patient"}' | create "$base/Patient" | jq -r '.id')

# update the patient twice to create a second and third version
echo "▶️update patient"
patient "$patient_id" "male" | update "$base/Patient/$patient_id" > /dev/null
echo "▶️update patient"
patient "$patient_id" "female" | update "$base/Patient/$patient_id" > /dev/null

# expect the history to contain three entries
history="$(fetch_instance_history "$base" Patient "$patient_id")"
test "instance history total" "$(echo "$history" | jq -r .total)" "3"
v3="$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')"
v2="$(echo "$history" | jq -r '.entry[1].resource.meta.versionId')"
v1="$(echo "$history" | jq -r '.entry[2].resource.meta.versionId')"

# expect deleting the current version to fail
test "delete current version status code" "$(delete_history_version "$patient_id" "$v3")" "409"

# delete the second version of the patient
test "delete second version status code" "$(delete_history_version "$patient_id" "$v2")" "204"

# expect the history to contain only the first and the current version
history="$(fetch_instance_history "$base" Patient "$patient_id")"
test "instance history total" "$(echo "$history" | jq -r .total)" "2"
test "instance history first entry versionId" "$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')" "$v3"
test "instance history first entry gender" "$(echo "$history" | jq -r '.entry[0].resource.gender')" "female"
test "instance history second entry versionId" "$(echo "$history" | jq -r '.entry[1].resource.meta.versionId')" "$v1"

# expect a versioned read of the deleted version to return 404
test "read deleted version status code" "$(vread "$patient_id" "$v2")" "404"

# expect versioned reads of the other versions to return 200
test "read first version status code" "$(vread "$patient_id" "$v1")" "200"
test "read current version status code" "$(vread "$patient_id" "$v3")" "200"

# expect the type history doesn't contain the deleted version
history="$(fetch_type_history "$base" Patient)"
test "type history first entry resource id" "$(echo "$history" | jq -r '.entry[0].resource.id')" "$patient_id"
test "type history first entry resource versionId" "$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')" "$v3"
test "type history second entry resource id" "$(echo "$history" | jq -r '.entry[1].resource.id')" "$patient_id"
test "type history second entry resource versionId" "$(echo "$history" | jq -r '.entry[1].resource.meta.versionId')" "$v1"

# expect the system history doesn't contain the deleted version
history="$(fetch_system_history "$base")"
test "system history first entry resource id" "$(echo "$history" | jq -r '.entry[0].resource.id')" "$patient_id"
test "system history first entry resource versionId" "$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')" "$v3"
test "system history second entry resource id" "$(echo "$history" | jq -r '.entry[1].resource.id')" "$patient_id"
test "system history second entry resource versionId" "$(echo "$history" | jq -r '.entry[1].resource.meta.versionId')" "$v1"

# expect deleting the deleted version again to have no effect
test "delete deleted version status code" "$(delete_history_version "$patient_id" "$v2")" "204"

# expect deleting a valid version id that isn't a version number to have no effect
test "delete non-numeric version status code" "$(delete_history_version "$patient_id" "a")" "204"

# expect deleting an invalid version id to be rejected
test "delete invalid version status code" "$(delete_history_version "$patient_id" "1_2")" "400"

# expect the history to still contain the first and the current version
test "instance history total" "$(fetch_instance_history "$base" Patient "$patient_id" | jq -r .total)" "2"
