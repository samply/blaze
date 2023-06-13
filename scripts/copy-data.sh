#!/bin/bash -e

# ### DISCLAIMER ###
#
# The error handling in this script is very basic. You have to verify
# that all resources are successfully transferred to your destination
# server after this script ends.
#

# Usage: copy-data.sh <source-base-url> <destination-base-url>

SRC_BASE_URI="$1"
DST_BASE_URI="$2"
PAGE_SIZE=1000
TRANSACTION_BUNDLE_SIZE=1000
NUM_TRANSACTION_JOBS=2

transact() {
  RESULT=$(jq -sc '{resourceType: "Bundle", type: "transaction", entry: .}' |\
    curl -s -w '%{http_code}' -o /dev/null -H 'Content-Type: application/fhir+json' -d @- "$1")

  if [ "$RESULT" = "200" ]; then
    echo "Successfully send transaction bundle $2"
  else
    echo "Error while sending transaction bundle $2"
  fi
}

export -f transact

echo "Copy all resources from $SRC_BASE_URI to $DST_BASE_URI..."

blazectl download --server "$SRC_BASE_URI" -q "_count=$PAGE_SIZE" 2>/dev/null |\
  jq -c '{resource: ., request: {method: "PUT", url: (.resourceType + "/" + .id)}}' |\
  parallel --pipe -n "$TRANSACTION_BUNDLE_SIZE" -j "$NUM_TRANSACTION_JOBS" transact "$DST_BASE_URI" "{#}"
