#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

CA_CERT="$SCRIPT_DIR/../ingress/blaze-cert.pem"

blazectl --no-progress \
  --server https://blaze.localhost/fhir \
  --certificate-authority "$CA_CERT" \
  --token "$("$SCRIPT_DIR/fetch-access-token.sh")" \
  upload "$SCRIPT_DIR/../../.github/test-data/synthea"
