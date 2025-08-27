#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

BASE="https://blaze.localhost/fhir"
CA_CERT="$SCRIPT_DIR/../ingress/blaze-cert.pem"
TOKEN="$("$SCRIPT_DIR/fetch-access-token.sh")"

blazectl --no-progress \
  --server "$BASE" \
  --certificate-authority "$CA_CERT" \
  --token "$TOKEN" \
  upload "$SCRIPT_DIR/../../test-data-synthea-100"
