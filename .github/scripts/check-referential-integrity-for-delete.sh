#!/bin/bash -e

# Creates a Patient and an Observation referring to this Patient. After that
# tries to delete the Patient. The status code of the delete response can be
# given as first argument. It should be 409 is referential integrity is checked
# and 204 if not.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
observation_id=$(uuidgen | tr '[:upper:]' '[:lower:]') 

curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$patient_id\"}" -o /dev/null "$base/Patient/$patient_id"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Observation\", \"id\": \"$observation_id\", \"subject\": {\"reference\": \"Patient/$patient_id\"}}" -o /dev/null "$base/Observation/$observation_id"
response_code=$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$base/Patient/$patient_id")

test "delete response" "$response_code" "$1"
