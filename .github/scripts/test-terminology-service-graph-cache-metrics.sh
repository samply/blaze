#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

URL="http://localhost:8081/metrics"

num-metrics() {
  NAME="$1"
  FILTER="$2"
  curl -s "$URL" | grep "$NAME" | grep -c "$FILTER"
}

# terminology service graph cache is available
test "blaze_cache_estimated_size terminology-service-graph-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"terminology-service-graph-cache\"")" "1"

# other caches are still available
test "blaze_cache_estimated_size tx-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"tx-cache\"")" "1"
test "blaze_cache_estimated_size resource-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"resource-cache\"")" "1"
