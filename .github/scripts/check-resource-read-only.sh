#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"


BASE="http://localhost:8080/fhir"
TYPE="$1"
URL="$2"

CODE_SYSTEM=$(curl -sH 'Accept: application/fhir+json' "$BASE/$TYPE?url=$URL")
ID=$(echo "$CODE_SYSTEM" | jq -r '.entry[0].resource.id')

test "error message" "$(curl -sXDELETE "$BASE/$TYPE/$ID" | jq -r '.issue[0].diagnostics')" "Can't delete the read-only resource \`$TYPE/$ID\`."
