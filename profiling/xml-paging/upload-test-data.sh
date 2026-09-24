#!/usr/bin/env bash

set -euo pipefail

server="${BLAZE_SERVER:-http://localhost:8080/fhir}"
data_dir="${1:-/Users/axs/Projekte/kerndatensatz-testdaten/Test_Data}"

if [[ ! -d "$data_dir" ]]; then
  echo "Data directory not found: $data_dir" >&2
  exit 1
fi

blazectl --no-progress --server "$server" upload "$data_dir"
blazectl --server "$server" count-resources
