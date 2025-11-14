#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

ca_cert_blaze="$script_dir/../../../modules/ingress/blaze-cert.pem"
ca_cert_keycloak="$script_dir/../../../modules/ingress/keycloak-cert.pem"
access_token=$(curl -s -d 'grant_type=client_credentials' --cacert "$ca_cert_keycloak" -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f "https://keycloak.localhost/realms/blaze/protocol/openid-connect/token" | jq -r .access_token)

if [ -z "$access_token" ]; then
  echo "Missing access token"
  exit 1;
fi

base="https://blaze.localhost/fhir"
column_family=$1
actual_name=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" "$base/__admin/dbs/index/column-families/$column_family/metadata" | jq -r .name)

test "column family name" "$actual_name" "$column_family"
