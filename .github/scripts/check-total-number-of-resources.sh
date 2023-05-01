#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

test "total number of resources" "$(curl -sH 'Accept: application/fhir+json' http://localhost:8080/fhir | jq -r .total)" "$1"
