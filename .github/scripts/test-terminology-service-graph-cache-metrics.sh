#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

url="http://localhost:8081/metrics"

num-metrics() {
  local name="$1"
  local filter="$2"
  curl -s "$url" | grep "$name" | grep -c "$filter"
}

# terminology service graph cache is available
test "blaze_cache_estimated_size terminology-service-graph-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"terminology-service-graph-cache\"")" "1"

# other caches are still available
test "blaze_cache_estimated_size tx-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"tx-cache\"")" "1"
test "blaze_cache_estimated_size resource-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"resource-cache\"")" "1"
