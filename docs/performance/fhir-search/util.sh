#!/bin/bash
set -euo pipefail

if [[ "$OSTYPE" == "darwin"* ]]; then
  time="gtime"
else
  time="time"
fi

calc-avg() {
  # Skip first line because it is used for warmup
  tail -n +2 "$1" |\
    awk '{sum += $1; sumsq += $1^2} END {printf("{\"avg\": %f, \"stddev\": %f}", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
}

restart() {
  docker compose -f "$1" restart
  sleep 30
}

calc-print-stats() {
  local times_file="$1"
  local count="$2"

  local stats
  stats="$(calc-avg "$times_file")"
  local avg
  avg=$(echo "$stats" | jq .avg)
  local stddev
  stddev=$(echo "$stats" | jq .stddev)

  awk -v count="$count" -v avg="$avg" -v stddev="$stddev" 'BEGIN {
    # resources per second
    res_s = count / avg

    # shorten the count
    if (count > 1000000) {
      human_count = sprintf("%4.1f M", count / 1000000)
    } else if (count > 1000) {
      human_count = sprintf("%4.0f k", count / 1000)
    } else {
      human_count = sprintf("%6.0f", count)
    }

    # shorten the resources per second
    if (res_s > 1000000) {
      human_res_s = sprintf("%4.1f M", res_s / 1000000)
    } else if (res_s > 1000) {
      human_res_s = sprintf("%4.1f k", res_s / 1000)
    } else {
      human_res_s = sprintf("%6.0f", res_s)
    }

    printf "| %s | %8.2f | %6.3f | %s |\n", human_count, avg, stddev, human_res_s
  }'
}

count-resources-raw() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file="$4"

  local count
  count=$(curl -s "$base/$resource_type?$search_params&_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for _ in {0..3}; do
    curl -s "$base/$resource_type?$search_params&_summary=count" -o /dev/null -w '%{time_total}\n' >> "$times_file"
  done

  calc-print-stats "$times_file" "$count"
}

count-resources-raw-post() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file="$4"
  local url="$base/$resource_type/_search?_summary=count"

  local count
  count=$(curl -s -d @<(echo -n "$search_params") "$url" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for _ in {0..3}; do
    curl -s -d @<(echo -n "$search_params") "$url" -o /dev/null -w '%{time_total}\n' >> "$times_file"
  done

  calc-print-stats "$times_file" "$count"
}

download-resources-raw() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file="$4"

  local count
  count=$(curl -s "$base/$resource_type?$search_params&_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for _ in {0..3}; do
    local download_count
    download_count=$($time -f "%e" -a -o "$times_file" blazectl download --server "$base" "$resource_type" -q "$search_params&_count=1000" 2>/dev/null | wc -l | xargs)

    if [ "$count" != "$download_count" ]; then
      echo "🆘 the number of downloaded resources ($download_count) doesn't match the count of $count"
      exit 1
    fi
  done

  calc-print-stats "$times_file" "$count"
}

download-resources-raw-post() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file="$4"

  local count
  count=$(curl -s -d @<(echo -n "$search_params") "$base/$resource_type/_search?_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for _ in {0..3}; do
    local download_count
    download_count=$($time -f "%e" -a -o "$times_file" blazectl download --server "$base" "$resource_type" -p -q @<(echo -n "$search_params&_count=1000") 2>/dev/null | wc -l | xargs)

    if [ "$count" != "$download_count" ]; then
      echo "🆘 the number of downloaded resources ($download_count) doesn't match the count of $count"
      exit 1
    fi
  done

  calc-print-stats "$times_file" "$count"
}

add_system() {
    local system="$1"
    local codes="$2"
    echo "$codes" | sed "s#^#${system}|#" | paste -sd ',' -
}
