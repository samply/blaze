#!/bin/bash -e
set -o pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
curl -sf -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"0\"}" -o /dev/null "$base/Patient/0"
