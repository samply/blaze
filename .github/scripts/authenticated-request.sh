#!/bin/bash -e

ACCESS_TOKEN=$(curl -s -d 'grant_type=client_credentials' -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f http://localhost:8090/auth/realms/blaze/protocol/openid-connect/token | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Missing access token"
  exit 1;
fi

test "Bundle" = "$(curl -s --oauth2-bearer "$ACCESS_TOKEN" http://localhost:8080/fhir | jq -r .resourceType)"
