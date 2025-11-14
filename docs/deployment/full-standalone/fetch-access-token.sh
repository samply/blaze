#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
ca_cert="$script_dir/../../../modules/ingress/keycloak-cert.pem"
realm_base="https://keycloak.localhost/realms/blaze"

client_id=${1:-account}
client_secret=${2:-e11a3a8e-6e24-4f9d-b914-da7619e8b31f}

response="$(curl -s -d 'grant_type=client_credentials' --cacert "$ca_cert" \
  -u "$client_id:$client_secret" \
  "$realm_base/protocol/openid-connect/token")"

if [ -n "$(echo "$response" | jq -r '.error // empty')" ]; then
  echo "Error: $(echo "$response" | jq -r .error_description)"
  exit 1
fi

echo "$response" | jq -r .access_token
