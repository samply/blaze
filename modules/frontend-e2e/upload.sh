#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

BASE="https://blaze.localhost/fhir"
CA_CERT="$SCRIPT_DIR/../ingress/blaze-cert.pem"
TOKEN="$("$SCRIPT_DIR/fetch-access-token.sh")"

blazectl --no-progress \
  --server "$BASE" \
  --certificate-authority "$CA_CERT" \
  --token "$TOKEN" \
  upload "$SCRIPT_DIR/../../.github/test-data/synthea"

echo "Download KDS Fall Package..."
wget -q --content-disposition "https://packages.simplifier.net/de.medizininformatikinitiative.kerndatensatz.fall/2025.0.0"
tar xzf de.medizininformatikinitiative.kerndatensatz.fall-2025.0.0.tgz

echo "Upload KDS Fall Profile..."
curl -sfH 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' --cacert "$CA_CERT" --oauth2-bearer "$TOKEN" -d @"package/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.json" "$BASE/StructureDefinition"
