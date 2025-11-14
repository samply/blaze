#!/bin/bash -e

# ### DISCLAIMER ###
#
# The error handling in this script is very basic. You have to verify
# that all resources are successfully transferred to your destination
# server after this script ends.
#

# Usage: copy-data.sh <source-base-url> <destination-base-url>

src_base_uri="$1"
dst_base_uri="$2"
page_size=1000
transaction_bundle_size=1000
num_transaction_jobs=2

transact() {
  local result=$(jq -sc '{resourceType: "Bundle", type: "transaction", entry: .}' |\
    curl -s -w '%{http_code}' -o /dev/null -H 'Content-Type: application/fhir+json' -d @- "$1")

  if [ "$result" = "200" ]; then
    echo "Successfully send transaction bundle $2"
  else
    echo "Error while sending transaction bundle $2"
  fi
}

export -f transact

echo "Copy all resources from $src_base_uri to $dst_base_uri..."

blazectl download --server "$src_base_uri" -q "_count=$page_size" 2>/dev/null |\
  jq -c '{resource: ., request: {method: "PUT", url: (.resourceType + "/" + .id)}}' |\
  parallel --pipe -n "$transaction_bundle_size" -j "$num_transaction_jobs" transact "$dst_base_uri" "{#}"
