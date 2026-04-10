#!/bin/bash
set -euo pipefail

port="${1:-8080}"

access_token=$(curl -sfH 'Accept: application/json' -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)

if [ -z "$access_token" ]; then
  echo "Missing access token"
  exit 1;
fi

base="http://localhost:${port}/fhir"

if [ "200" = "$(curl -s --oauth2-bearer "$access_token" -o /dev/null -w '%{response_code}' "$base")" ]; then
  echo "✅ successful authenticated system search request"
else
  echo "🆘 failed authenticated system search request"
  exit 1
fi

if [ "200" = "$(curl -s --oauth2-bearer "$access_token" -H "Content-Type: application/fhir+json" -d @.github/openid-auth-test/batch-bundle.json "$base" | jq -r '.entry[].response.status')" ]; then
  echo "✅ successful authenticated batch request"
else
  echo "🆘 failed authenticated batch request"
  exit 1
fi
