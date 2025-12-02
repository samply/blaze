#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# create a patient
id="$(uuidgen)"
curl -sfXPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$id\"}" -o /dev/null "$base/Patient/$id"

# if the query param __t was respected, the patient should not exist
curl -sfH 'Accept: application/fhir+json' "$base/Patient/$id?__t=0" -o /dev/null
