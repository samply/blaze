#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"0\"}" -o /dev/null "$BASE/Patient/0"
