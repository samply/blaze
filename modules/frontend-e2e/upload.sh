#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"

base="https://blaze.localhost/fhir"
ca_cert="$script_dir/../ingress/blaze-cert.pem"
token="$("$script_dir/fetch-access-token.sh")"

blazectl --no-progress \
  --server "$base" \
  --certificate-authority "$ca_cert" \
  --token "$token" \
  upload "$script_dir/../../.github/test-data/synthea"

echo "Upload KDS Fall Profile..."
curl -sfH 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' --cacert "$ca_cert" --oauth2-bearer "$token" -d @"$script_dir/test-data/node_modules/de.medizininformatikinitiative.kerndatensatz.fall/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.json" "$base/StructureDefinition"

echo "Upload one Value Set..."
curl -sfH 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' --cacert "$ca_cert" --oauth2-bearer "$token" -d @"$script_dir/test-data/node_modules/de.medizininformatikinitiative.kerndatensatz.laborbefund/ValueSet-mii-vs-labor-laborbereich.json" "$base/ValueSet"
