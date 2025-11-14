#!/bin/bash -e

# Usage: save-data.sh <source-base-url> <destination-dir>

src_base_uri="$1"
dst_dir="$2"
page_size=1000
num_jobs=2

save-bundle() {
  jq -sc '{resourceType: "Bundle", type: "transaction", entry: .}' | gzip > "$1/transaction-$2.json.gz"
  echo "Successfully saved transaction bundle $2"
}

export -f save-bundle

echo "Save all resources from $src_base_uri into the directory $dst_dir..."

blazectl download --server "$src_base_uri" -q "_count=$page_size" 2>/dev/null |\
  jq -c '{resource: ., request: {method: "PUT", url: (.resourceType + "/" + .id)}}' |\
  parallel --pipe -n "$transaction_bundle_size" -j "$num_jobs" save-bundle "$dst_dir" "{#}"
