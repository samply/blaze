#!/bin/bash
set -euo pipefail

# Uploads the KDS test data transaction bundle, retrying once on failure.
#
# The upload sometimes fails with a 503 "The external validator is currently
# unavailable." after nearly all entries were validated, while the validator
# itself is up. The retry is safe, because a failed transaction commits nothing
# and the bundle only consists of PUT requests, which are idempotent.
#
# Remove the retry once the cause is fixed: https://github.com/samply/blaze/issues/4766

data_dir="${1:-.github/test-data/kds-testdata-2024.0.1/tx}"

upload() {
  blazectl --no-progress --server http://localhost:8080/fhir upload "$data_dir"
}

if ! upload; then
  echo "::warning::Upload of the KDS test data failed. Retrying once. See https://github.com/samply/blaze/issues/4766"
  upload
fi
