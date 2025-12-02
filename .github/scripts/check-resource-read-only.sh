#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"


base="http://localhost:8080/fhir"
type="$1"
url="$2"

code_system=$(curl -sH 'Accept: application/fhir+json' "$base/$type?url=$url")
id=$(echo "$code_system" | jq -r '.entry[0].resource.id')

test "error message" "$(curl -sXDELETE "$base/$type/$id" | jq -r '.issue[0].diagnostics')" "Can't delete the read-only resource \`$type/$id\`."
