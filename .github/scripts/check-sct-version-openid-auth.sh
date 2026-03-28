#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

access_token=$(curl -sfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)

if [ -z "$access_token" ]; then
  echo "Missing access token"
  exit 1;
fi

version="$1"
base="${2:-http://localhost:8080/fhir}"
terminology_capabilities=$(curl -sH 'Accept: application/fhir+json' --oauth2-bearer "$access_token" "$base/metadata?mode=terminology")

test "SCT version" "$(echo "$terminology_capabilities" | jq -r '.codeSystem[] | select(.uri == "http://snomed.info/sct").version[0].code' )" "$version"
