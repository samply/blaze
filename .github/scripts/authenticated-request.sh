#!/bin/bash -e

ACCESS_TOKEN=$(curl -s -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/realms/blaze/protocol/openid-connect/token | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Missing access token"
  exit 1;
fi

BASE="http://localhost:8080/fhir"

if [ "200" = "$(curl -s --oauth2-bearer "$ACCESS_TOKEN" -o /dev/null -w '%{response_code}' "$BASE")" ]; then
  echo "✅ successful authenticated system search request"
else
  echo "🆘 failed authenticated system search request"
  exit 1
fi

if [ "200" = "$(curl -s --oauth2-bearer "$ACCESS_TOKEN" -H "Content-Type: application/fhir+json" -d @.github/openid-auth-test/batch-bundle.json "$BASE" | jq -r '.entry[].response.status')" ]; then
  echo "✅ successful authenticated batch request"
else
  echo "🆘 failed authenticated batch request"
  exit 1
fi
