#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

url="http://localhost:8081/metrics"

num-metrics() {
  local name="$1"
  local filter="$2"
  curl -s "$url" | grep "$name" | grep -c "$filter"
}

test "blaze_rocksdb_block_cache_data_miss index" "$(num-metrics "blaze_rocksdb_block_cache_data_miss" "name=\"index\"")" "1"
test "blaze_rocksdb_block_cache_data_miss transaction" "$(num-metrics "blaze_rocksdb_block_cache_data_miss" "name=\"transaction\"")" "1"
test "blaze_rocksdb_block_cache_data_miss resource" "$(num-metrics "blaze_rocksdb_block_cache_data_miss" "name=\"resource\"")" "1"

test "blaze_cache_estimated_size tx-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"tx-cache\"")" "1"
test "blaze_cache_estimated_size resource-cache" "$(num-metrics "blaze_cache_estimated_size" "name=\"resource-cache\"")" "1"
