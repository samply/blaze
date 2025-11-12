#!/bin/bash -e

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

  # resources per second
  local res_s
  res_s=$(echo "scale=2; $count / $avg" | bc)

  # shorten the count
  local count_format
  if (( $(echo "$count > 1000000" | bc) )); then
    count=$(echo "scale=2; $count / 1000000" | bc)
    count_format="%4.1f M"
  elif (( $(echo "$count > 1000" | bc) )); then
    count=$(echo "scale=2; $count / 1000" | bc)
    count_format="%4.0f k"
  else
    count_format="%6.0f"
  fi

  # shorten the resources per second
  local res_s_format
  if (( $(echo "$res_s > 1000000" | bc) )); then
    res_s=$(echo "scale=2; $res_s / 1000000" | bc)
    res_s_format="%4.1f M"
  elif (( $(echo "$res_s > 1000" | bc) )); then
    res_s=$(echo "scale=2; $res_s / 1000" | bc)
    res_s_format="%4.1f k"
  else
    res_s_format="%6.0f"
  fi

  printf "| $count_format | %8.2f | %6.3f | $res_s_format |\n" "$count" "$avg" "$(echo "$stats" | jq .stddev)" "$res_s"
}

count-resources-raw() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file="$4"

  local count
  count=$(curl -s "$base/$resource_type?$search_params&_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for i in {0..3}; do
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
  for i in {0..3}; do
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
  for i in {0..3}; do
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
  for i in {0..3}; do
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
