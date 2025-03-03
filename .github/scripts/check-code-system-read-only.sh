#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"


BASE="http://localhost:8080/fhir"
URL="$1"

CODE_SYSTEM=$(curl -sH 'Accept: application/fhir+json' "$BASE/CodeSystem?url=$URL")
ID=$(echo "$CODE_SYSTEM" | jq -r '.entry[0].resource.id')

test "error message" "$(curl -sXDELETE "$BASE/CodeSystem/$ID" | jq -r '.issue[0].diagnostics')" "Can't delete the read-only resource \`CodeSystem/$ID\`."
