#!/bin/bash
set -euo pipefail

base="http://localhost:8080/fhir"

blazectl --server "$base" download Patient -q '_elements=id&_count=1000' 2>/dev/null | \
  jq -r '.id' | \
  xargs -P 4 -I {} curl -s -X POST "$base/Patient/{}/\$purge" -o /dev/null
