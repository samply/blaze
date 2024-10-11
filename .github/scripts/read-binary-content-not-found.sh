#!/bin/bash -e

# This script queries the server for a non-existent binary resource
# and verifies that we get the 404 error message.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

RANDOM_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"

# Attempt to retrieve the Binary resource by ID
echo "Verifying that the Binary resource with ID '$RANDOM_ID' does not exist."

# Perform a GET request to retrieve the Binary resource by ID
STATUS_CODE=$(curl -s -H "Accept: application/pdf" -o /dev/null -w '%{response_code}' "$BASE/Binary/$RANDOM_ID")

# Test that the response code is 404 (Not Found), indicating the resource doesn't exist
test "GET response code for Binary resource" "$STATUS_CODE" "404"
