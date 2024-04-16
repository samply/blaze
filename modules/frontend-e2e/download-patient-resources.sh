#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../../.github/scripts/util.sh"

CA_CERT="$SCRIPT_DIR/../ingress/blaze-cert.pem"

BASE="https://blaze.localhost/fhir"
ACCESS_TOKEN="$("$SCRIPT_DIR/fetch-access-token.sh")"
EXPECTED_SIZE=$(curl -s --cacert "$CA_CERT" --oauth2-bearer "$ACCESS_TOKEN" "$BASE/Patient?_summary=count" | jq -r .total)
ACTUAL_SIZE=$(blazectl --server "$BASE" \
  --certificate-authority "$CA_CERT" \
  --token "$ACCESS_TOKEN" \
  download Patient -q "_count=10" 2>/dev/null | wc -l | xargs)

test "download size" "$ACTUAL_SIZE" "$EXPECTED_SIZE"
