#!/bin/sh

curl -s -d 'grant_type=client_credentials' \
  -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f \
  http://localhost:8090/realms/blaze/protocol/openid-connect/token |\
  jq -r .access_token
