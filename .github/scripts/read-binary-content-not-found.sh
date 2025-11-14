#!/bin/bash -e

# This script queries the server for a non-existent binary resource
# and verifies that we get the 404 error message.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

random_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"

# Attempt to retrieve the Binary resource by ID
echo "Verifying that the Binary resource with ID '$random_id' does not exist."

# Perform a GET request to retrieve the Binary resource by ID
status_code=$(curl -s -H "Accept: application/pdf" -o /dev/null -w '%{response_code}' "$base/Binary/$random_id")

# Test that the response code is 404 (Not Found), indicating the resource doesn't exist
test "GET response code for Binary resource" "$status_code" "404"
