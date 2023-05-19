#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

# create a patient
ID="$(uuidgen)"
curl -sfXPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$ID\"}" -o /dev/null "$BASE/Patient/$ID"

# if the query param __t was respected, the patient should not exist
curl -sfH 'Accept: application/fhir+json' "$BASE/Patient/$ID?__t=0" -o /dev/null
