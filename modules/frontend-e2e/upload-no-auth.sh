#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"

# without access token, because the backend runs without authentication
blazectl --no-progress \
  --server "https://blaze.localhost/fhir" \
  --certificate-authority "$script_dir/../ingress/blaze-cert.pem" \
  upload "$script_dir/../../.github/test-data/synthea"
