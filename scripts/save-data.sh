#!/bin/bash -e

# Usage: save-data.sh <source-base-url> <destination-dir>

SRC_BASE_URI="$1"
DST_DIR="$2"
PAGE_SIZE=1000
NUM_JOBS=2

save-bundle() {
  jq -sc '{resourceType: "Bundle", type: "transaction", entry: .}' | gzip > "$1/transaction-$2.json.gz"
  echo "Successfully saved transaction bundle $2"
}

export -f save-bundle

echo "Save all resources from $SRC_BASE_URI into the directory $DST_DIR..."

blazectl download --server "$SRC_BASE_URI" -q "_count=$PAGE_SIZE" 2>/dev/null |\
  jq -c '{resource: ., request: {method: "PUT", url: (.resourceType + "/" + .id)}}' |\
  parallel --pipe -n "$TRANSACTION_BUNDLE_SIZE" -j "$NUM_JOBS"  save-bundle "$DST_DIR" "{#}"
