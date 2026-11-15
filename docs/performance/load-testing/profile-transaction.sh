#!/bin/bash
set -euo pipefail

# Profiles Blaze of the given system with async-profiler while the transaction
# test runs at a single concurrency level.
#
# Restarts Blaze with an empty database and with the agent of async-profiler
# loaded via docker-compose-profiling.yml, runs transaction.js with k6 and
# starts the profiler after the warmup in the setup of the test, so that the
# profile covers the measured scenario only. Stops the profiler when k6 exits.
#
# Writes four files into the current directory, named
# transaction-<system>-c<VUS>:
#  <name>.log     - the output of k6, with the throughput and the latencies of
#                   the profiled run
#  <name>.jfr     - the recording in the JDK Flight Recorder format, with every
#                   sample including its thread and timestamp
#  <name>.folded  - the folded stacks with the thread as first frame
#  <name>.html    - an interactive flame graph
#
# Environment variables:
#  VUS                  - the concurrency level to profile; required, because
#                         each level has its own ratio of per-batch to
#                         per-entry work, which a profile over the whole sweep
#                         would blur
#  BASE, DURATION,
#  WARMUP               - passed on to transaction.js
#  PROFILE_EVENT        - the async-profiler event, like `cpu` or `alloc`;
#                         defaults to `wall`
#  PROFILE_INTERVAL     - the sampling interval of the event; defaults to the
#                         one of async-profiler
#  ASYNC_PROFILER_HOME  - the directory of async-profiler inside the blaze
#                         container; defaults to `/opt/async-profiler`
#
# Usage: VUS=128 ./profile-transaction.sh <system>

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../fhir-search/util.sh"

system="$1"
compose_file="$script_dir/docker-compose-$system.yml"

if ! [[ "${VUS:-}" =~ ^[1-9][0-9]*$ ]]; then
  echo "🆘 VUS has to be a positive integer but was: ${VUS:-}"
  exit 1
fi

"$script_dir/reset.sh" "$system" "$script_dir/docker-compose-profiling.yml"

name="transaction-$system-c$VUS"
log_file="$name.log"

# empties the log of an earlier run, whose end of the warmup would match below
: > "$log_file"
# the subshell fails with the exit code of k6, because it inherits pipefail
(k6 run "$script_dir/transaction.js" 2>&1 | tee "$log_file") &
k6_pid=$!

# Waits until k6 logs the end of the warmup. Fails with the exit code of k6 if
# it exits before.
until grep -q "warmup finished" "$log_file"; do
  if ! kill -0 "$k6_pid" 2> /dev/null; then
    wait "$k6_pid"
    echo "🆘 k6 exited before the end of the warmup"
    exit 1
  fi
  sleep 0.1
done

arm-profiling "$compose_file" "$name"

trap abort-profiling EXIT
start-profiling
wait "$k6_pid"
trap - EXIT

stop-profiling
