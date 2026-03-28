#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

access_token=$(curl -sfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)

if [ -z "$access_token" ]; then
  echo "Missing access token"
  exit 1;
fi

base="${1:-http://localhost:8080/fhir}"
terminology_capabilities=$(curl -sH 'Accept: application/fhir+json' --oauth2-bearer "$access_token" "$base/metadata?mode=terminology")

test "resourceType" "$(echo "$terminology_capabilities" | jq -r .resourceType)" "TerminologyCapabilities"
test "status" "$(echo "$terminology_capabilities" | jq -r .status)" "active"
test "kind" "$(echo "$terminology_capabilities" | jq -r .kind)" "instance"
test "software name" "$(echo "$terminology_capabilities" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$terminology_capabilities" | jq -r .implementation.url)" "http://localhost:8080/fhir"

test "BCP-13 version" "$(echo "$terminology_capabilities" | jq -r '.codeSystem[] | select(.uri == "urn:ietf:bcp:13").version[0].code' )" "1.0.0"
test "BCP-47 version" "$(echo "$terminology_capabilities" | jq -r '.codeSystem[] | select(.uri == "urn:ietf:bcp:47").version[0].code' )" "1.0.0"
test "LOINC version" "$(echo "$terminology_capabilities" | jq -r '.codeSystem[] | select(.uri == "http://loinc.org").version[0].code' )" "2.78"
test "UCUM version" "$(echo "$terminology_capabilities" | jq -r '.codeSystem[] | select(.uri == "http://unitsofmeasure.org").version[0].code' )" "2013.10.21"
