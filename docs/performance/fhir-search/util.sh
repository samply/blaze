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

# Prints the average and the standard deviation of the durations in the times
# file as JSON, skipping the ones of the warmup runs at its start, whose number
# is given as optional second argument, which defaults to 1.
calc-avg() {
  local warmups="${2:-1}"
  tail -n +"$((warmups + 1))" "$1" |\
    awk '{sum += $1; sumsq += $1^2} END {printf("{\"avg\": %f, \"stddev\": %f}", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
}

# Returns the number of runs of a test that are taken for the statistics. Each
# test runs once more for warmup. Set the environment variable REPEATS to
# override the default, like to get more samples while profiling fast tests.
# The default is given as argument.
repeats() {
  local repeats="${REPEATS:-$1}"
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

# Recreates the Blaze container of the compose file and waits until it's
# ready, so that Blaze starts with empty in-memory caches.
recreate() {
  docker compose -f "$1" up --force-recreate --wait
}

# Stops Blaze of the compose file given as first argument, runs the reset
# script given as second argument and recreates Blaze, so that it starts with
# the database the reset script left.
reset-and-recreate() {
  docker compose -f "$1" stop
  "$2"
  recreate "$1"
}

# Restarts Blaze. While only some tests run, only records the restart, so that
# `profile` does it before the test it runs and not before the tests it skips.
restart() {
  if [[ -n "$(selected-tests)" ]]; then
    pending_restart="$1"
    return
  fi

  recreate "$1"
}

# Restarts Blaze like `restart`, but runs the reset script given as second
# argument while Blaze is stopped, like one that resets the database to its
# state before the first test.
reset-and-restart() {
  if [[ -n "$(selected-tests)" ]]; then
    pending_restart="$1"
    pending_reset="$2"
    return
  fi

  reset-and-recreate "$1" "$2"
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

# Starts the profiler after the run of a test with the index given as first
# argument, if it's the last of the warmup runs, which aren't taken for the
# statistics. Their number is given as optional second argument, which defaults
# to 1, so that the profiler starts after the run with index 0.
start-profiling-after-warmup() {
  local warmups="${2:-1}"
  if [[ "$1" -eq "$((warmups - 1))" ]]; then
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
  run-asprof "$profiling_compose_file" start -e "${PROFILE_EVENT:-wall}" ${PROFILE_INTERVAL:+-i "$PROFILE_INTERVAL"} -o jfr -f "/tmp/$profiling_name.jfr"
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
#  PROFILE_INTERVAL     - the sampling interval of the event, like `5ms` for
#                         `wall` and `cpu` or `512k` bytes for `alloc`; a
#                         shorter interval gives more samples at a higher
#                         overhead; defaults to the one of async-profiler,
#                         which is `50ms` for `wall`
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

  if [[ -n "${pending_reset:-}" ]]; then
    reset-and-recreate "$pending_restart" "$pending_reset"
    pending_restart=""
    pending_reset=""
  elif [[ -n "${pending_restart:-}" ]]; then
    recreate "$pending_restart"
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

# Prints the statistics of the durations in the times file, as a row of the
# results tables, with the count of resources given as second argument. Skips
# the durations of the warmup runs, whose number is given as optional third
# argument, which defaults to 1. Prints the average duration with the number of
# decimal digits given as optional fourth argument, which defaults to 2.
calc-print-stats() {
  local times_file="$1"
  local count="$2"
  local warmups="${3:-1}"
  local time_digits="${4:-2}"

  local stats
  stats="$(calc-avg "$times_file" "$warmups")"
  local avg
  avg=$(echo "$stats" | jq .avg)
  local stddev
  stddev=$(echo "$stats" | jq .stddev)

  awk -v count="$count" -v avg="$avg" -v stddev="$stddev" -v time_digits="$time_digits" 'BEGIN {
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

    # the precision of the duration is part of the format, because not every
    # awk supports giving it as an argument with *
    format = "| %s | %8." time_digits "f | %6.3f | %s |\n"
    printf format, human_count, avg, stddev, human_res_s
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
  repeats="$(repeats 3)"
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
  repeats="$(repeats 3)"
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
  repeats="$(repeats 3)"
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
  repeats="$(repeats 3)"
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
