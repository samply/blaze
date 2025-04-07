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

echo "Upload KDS Fall Profile..."
curl -sfH 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' --cacert "$CA_CERT" --oauth2-bearer "$TOKEN" -d @"$SCRIPT_DIR/node_modules/de.medizininformatikinitiative.kerndatensatz.fall/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.json" "$BASE/StructureDefinition"

echo "Upload one Value Set..."
curl -sfH 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' --cacert "$CA_CERT" --oauth2-bearer "$TOKEN" -d @"$SCRIPT_DIR/node_modules/de.medizininformatikinitiative.kerndatensatz.laborbefund/ValueSet-mii-vs-labor-laborbereich.json" "$BASE/ValueSet"
