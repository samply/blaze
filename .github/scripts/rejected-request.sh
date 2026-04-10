#!/bin/bash
set -euo pipefail

port="${1:-8081}"

access_token=$(curl -sfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)

if [ -z "$access_token" ]; then
  echo "Missing access token"
  exit 1
fi

base="http://localhost:${port}/fhir"

if [ "401" = "$(curl -s --oauth2-bearer "$access_token" -o /dev/null -w '%{response_code}' "$base")" ]; then
  echo "✅ request correctly rejected with 401"
else
  echo "🆘 expected 401"
  exit 1
fi
