#!/bin/bash
set -euo pipefail

# Exits with a message if blazectl isn't installed, because the downloads hide
# the errors of blazectl to keep its progress output away.
require-blazectl() {
  if ! command -v blazectl > /dev/null; then
    echo "🆘 blazectl isn't installed"
    exit 1
  fi
}

calc-avg() {
  # Skip first line because it is used for warmup
  tail -n +2 "$1" |\
    awk '{sum += $1; sumsq += $1^2} END {printf("{\"avg\": %f, \"stddev\": %f}", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
}

# Returns the number of runs of a test that are taken for the statistics. Each
# test runs once more for warmup. Set the environment variable REPEATS to
# override the default of 3, like to get more samples while profiling fast
# tests.
repeats() {
  local repeats="${REPEATS:-3}"
  if ! [[ "$repeats" =~ ^[1-9][0-9]*$ ]]; then
    echo "🆘 REPEATS has to be a positive integer but was: $repeats" >&2
    exit 1
  fi
  echo "$repeats"
}

# Returns the glob pattern of the names of the tests to run: TEST if set,
# otherwise PROFILE, so that profiling runs only the profiled tests. Returns
# nothing if all tests run.
selected-tests() {
  echo "${TEST:-${PROFILE:-}}"
}

# Restarts Blaze. While only some tests run, only records the restart, so that
# `profile` does it before the test it runs and not before the tests it skips.
restart() {
  if [[ -n "$(selected-tests)" ]]; then
    pending_restart="$1"
    return
  fi

  docker compose -f "$1" up --force-recreate --wait
}

async-profiler-home() {
  echo "${ASYNC_PROFILER_HOME:-/opt/async-profiler}"
}

run-asprof() {
  local compose_file="$1"
  shift

  docker compose -f "$compose_file" exec -T blaze "$(async-profiler-home)/bin/asprof" "$@" 1 > /dev/null
}

# Converts the JFR file to the format of the extension of the output file.
run-jfrconv() {
  local compose_file="$1"
  shift

  docker compose -f "$compose_file" exec -T blaze "$(async-profiler-home)/bin/jfrconv" "$@" > /dev/null
}

# Returns the option of jfrconv selecting the samples of the async-profiler
# event. Other events, like hardware counters, are recorded as CPU samples.
jfrconv-event-option() {
  case "$1" in
    wall|alloc|lock) echo "--$1" ;;
    *) echo "--cpu" ;;
  esac
}

# Starts the profiler after the first run with index 0 of a test, which is the
# warmup that isn't taken for the statistics.
start-profiling-after-warmup() {
  if [[ "$1" -eq 0 ]]; then
    start-profiling
  fi
}

# Starts the profiler armed by `profile` unless it's already started. Called by
# the tests after their warmup, so that only the measured runs are profiled.
# Does nothing if no profiler is armed.
start-profiling() {
  if [[ -z "${profiling_name:-}" || -n "${profiling_started:-}" ]]; then
    return
  fi

  # JFR is written while recording, so its file is given on start
  run-asprof "$profiling_compose_file" start -e "${PROFILE_EVENT:-wall}" -o jfr -f "/tmp/$profiling_name.jfr"
  profiling_started=1
}

# Stops the profiler if it was started, so that the next run can start it
# again. Used if a test fails.
abort-profiling() {
  if [[ -n "${profiling_started:-}" ]]; then
    run-asprof "$profiling_compose_file" stop
  fi
}

# Runs the test command with the name given as second argument if the name
# matches the glob pattern in the environment variable TEST, or in PROFILE if
# TEST isn't set. Skips the test otherwise. Runs all tests if neither is set.
#
# Runs the test under async-profiler if the name also matches the glob pattern
# in PROFILE.
#
# A restart requested before the test is done right before it, so that a
# selected test starts on a freshly restarted Blaze.
#
# The test starts the profiler itself after its warmup by calling
# `start-profiling`, so that the profile covers the measured runs only.
#
# Writes three files into the current directory:
#  <name>.jfr     - the recording in the JDK Flight Recorder format, with every
#                   sample including its thread and timestamp
#  <name>.folded  - the folded stacks with the thread as first frame
#  <name>.html    - an interactive flame graph
#
# Environment variables:
#  TEST                 - glob pattern of the names of the tests to run, like
#                         `count-72514-3` or `count-*`; defaults to PROFILE
#  PROFILE              - glob pattern of the names of the tests to profile;
#                         nothing is profiled if unset
#  PROFILE_EVENT        - the async-profiler event, like `cpu` or `alloc`;
#                         defaults to `wall`
#  REPEATS              - the number of runs after the warmup, which are the
#                         profiled ones; defaults to 3
#  ASYNC_PROFILER_HOME  - the directory of async-profiler inside the blaze
#                         container; defaults to `/opt/async-profiler`
#
# Example:
#  profile "$compose_file" "count-72514-3" count-resources "72514-3"
profile() {
  local compose_file="$1"
  local name="$2"
  shift 2

  local selected
  selected="$(selected-tests)"
  # shellcheck disable=SC2053
  if [[ -n "$selected" && "$name" != $selected ]]; then
    return
  fi

  if [[ -n "${pending_restart:-}" ]]; then
    docker compose -f "$pending_restart" up --force-recreate --wait
    pending_restart=""
  fi

  # shellcheck disable=SC2053
  if [[ -z "${PROFILE:-}" || "$name" != $PROFILE ]]; then
    "$@"
    return
  fi

  profiling_compose_file="$compose_file"
  profiling_name="$name"
  profiling_started=""

  trap abort-profiling EXIT
  "$@"
  trap - EXIT

  if [[ -z "$profiling_started" ]]; then
    echo "🆘 the test $name didn't start the profiler"
    exit 1
  fi

  run-asprof "$compose_file" stop
  profiling_name=""
  profiling_started=""

  local event_option
  event_option="$(jfrconv-event-option "${PROFILE_EVENT:-wall}")"
  local container_base="/tmp/$name"
  run-jfrconv "$compose_file" "$event_option" --threads -o collapsed "$container_base.jfr" "$container_base.folded"
  run-jfrconv "$compose_file" "$event_option" -o html "$container_base.jfr" "$container_base.html"

  local extension
  for extension in jfr folded html; do
    docker compose -f "$compose_file" cp "blaze:$container_base.$extension" "$name.$extension" 2> /dev/null
  done

  echo "Profile written to $name.jfr, $name.folded and $name.html"
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
  local times_file
  times_file="$(mktemp)"

  local count
  count=$(curl -s "$base/$resource_type?$search_params&_summary=count" | jq .total)

  # the first run is the warmup which isn't taken for the statistics
  local i repeats
  repeats="$(repeats)"
  for ((i = 0; i <= repeats; i++)); do
    curl -s "$base/$resource_type?$search_params&_summary=count" -o /dev/null -w '%{time_total}\n' >> "$times_file"
    start-profiling-after-warmup "$i"
  done

  calc-print-stats "$times_file" "$count"
  rm "$times_file"
}

count-resources-raw-post() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file
  times_file="$(mktemp)"
  local url="$base/$resource_type/_search?_summary=count"

  local count
  count=$(curl -s -d @<(echo -n "$search_params") "$url" | jq .total)

  # the first run is the warmup which isn't taken for the statistics
  local i repeats
  repeats="$(repeats)"
  for ((i = 0; i <= repeats; i++)); do
    curl -s -d @<(echo -n "$search_params") "$url" -o /dev/null -w '%{time_total}\n' >> "$times_file"
    start-profiling-after-warmup "$i"
  done

  calc-print-stats "$times_file" "$count"
  rm "$times_file"
}

download-resources-raw() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file
  times_file="$(mktemp)"

  require-blazectl

  local count
  count=$(curl -s "$base/$resource_type?$search_params&_summary=count" | jq .total)

  # the time keyword of bash appends the elapsed seconds to the times file, so
  # that no external time command is needed
  local TIMEFORMAT="%R"

  # the first run is the warmup which isn't taken for the statistics
  local i repeats
  repeats="$(repeats)"
  for ((i = 0; i <= repeats; i++)); do
    local download_count
    download_count=$({ time blazectl download --server "$base" "$resource_type" -q "$search_params&_count=1000" 2>/dev/null | wc -l | xargs; } 2>> "$times_file")

    if [ "$count" != "$download_count" ]; then
      echo "🆘 the number of downloaded resources ($download_count) doesn't match the count of $count"
      exit 1
    fi
    start-profiling-after-warmup "$i"
  done

  calc-print-stats "$times_file" "$count"
  rm "$times_file"
}

download-resources-raw-post() {
  local base="$1"
  local resource_type="$2"
  local search_params="$3"
  local times_file
  times_file="$(mktemp)"

  require-blazectl

  local count
  count=$(curl -s -d @<(echo -n "$search_params") "$base/$resource_type/_search?_summary=count" | jq .total)

  # the time keyword of bash appends the elapsed seconds to the times file, so
  # that no external time command is needed
  local TIMEFORMAT="%R"

  # the first run is the warmup which isn't taken for the statistics
  local i repeats
  repeats="$(repeats)"
  for ((i = 0; i <= repeats; i++)); do
    local download_count
    download_count=$({ time blazectl download --server "$base" "$resource_type" -p -q @<(echo -n "$search_params&_count=1000") 2>/dev/null | wc -l | xargs; } 2>> "$times_file")

    if [ "$count" != "$download_count" ]; then
      echo "🆘 the number of downloaded resources ($download_count) doesn't match the count of $count"
      exit 1
    fi
    start-profiling-after-warmup "$i"
  done

  calc-print-stats "$times_file" "$count"
  rm "$times_file"
}

add_system() {
    local system="$1"
    local codes="$2"
    echo "$codes" | sed "s#^#${system}|#" | paste -sd ',' -
}
