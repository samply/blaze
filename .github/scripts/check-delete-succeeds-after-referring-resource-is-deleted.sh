#!/bin/bash -e

# Creates a Patient and an Observation referring to this Patient. After that
# tries to delete the Patient. This delete should result in a 409. After that
# it deleted the referring Observation, which should free up the Patient.
# Finally the script tries to delete the Patient again and expects that request
# to succeed.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
observation_id=$(uuidgen | tr '[:upper:]' '[:lower:]') 

curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$patient_id\"}" -o /dev/null "$base/Patient/$patient_id"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Observation\", \"id\": \"$observation_id\", \"subject\": {\"reference\": \"Patient/$patient_id\"}}" -o /dev/null "$base/Observation/$observation_id"

test "first delete Patient response" "$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$base/Patient/$patient_id")" "409"
test "delete Observation response" "$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$base/Observation/$observation_id")" "204"
test "second delete Patient response" "$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$base/Patient/$patient_id")" "204"
