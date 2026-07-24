#!/bin/bash
set -euo pipefail

# runs a small disk performance measurement via the disk-perf subcommand of
# blazectl and inspects the output parameters and the resulting job

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# blazectl runs the $disk-perf operation, polls the async status and prints the
# raw output Parameters resource with -o json. The random read sweep runs with
# the default max-concurrency of 32, so the levels are 1, 2, 4, 8, 16 and 32.
output=$(blazectl --server "$base" disk-perf index --file-size 0.125 --phase-duration 3 -o json)

test "output resource type" "$(echo "$output" | jq -r '.resourceType')" "Parameters"

param_expr() {
  echo ".parameter[] | select(.name == \"$1\")"
}

# selects the part $2 of the rand-read parameter with concurrency $1
rand_read_part() {
  echo ".parameter[] | select(.name == \"rand-read\") | select(any(.part[]; .name == \"concurrency\" and .valuePositiveInt == $1)) | .part[] | select(.name == \"$2\")"
}

test "score range" "$(echo "$output" | jq "$(param_expr "score") | .valueDecimal >= 0 and .valueDecimal <= 100")" "true"
test_regex "rating" "$(echo "$output" | jq -r "$(param_expr "rating") | .valueCode")" "^(excellent|good|acceptable|insufficient)$"
test "seq-write-throughput positive" "$(echo "$output" | jq "$(param_expr "seq-write-throughput") | .valueQuantity.value > 0")" "true"
test "seq-write-throughput unit code" "$(echo "$output" | jq -r "$(param_expr "seq-write-throughput") | .valueQuantity.code")" "By/s"
test "rand-read sweep concurrencies" "$(echo "$output" | jq -r '[.parameter[] | select(.name == "rand-read") | .part[] | select(.name == "concurrency") | .valuePositiveInt] | join(" ")')" "1 2 4 8 16 32"
test "rand-read iops at 1 reader positive" "$(echo "$output" | jq "$(rand_read_part 1 "iops") | .valueQuantity.value > 0")" "true"
test "rand-read iops unit code" "$(echo "$output" | jq -r "$(rand_read_part 1 "iops") | .valueQuantity.code")" "/s"
test "rand-read throughput at 32 readers positive" "$(echo "$output" | jq "$(rand_read_part 32 "throughput") | .valueQuantity.value > 0")" "true"
test "rand-read latency-p50 unit code" "$(echo "$output" | jq -r "$(rand_read_part 1 "latency-p50") | .valueQuantity.code")" "us"
test "rand-read latency percentiles monotone" "$(echo "$output" | jq "[($(rand_read_part 1 "latency-p50") | .valueQuantity.value), ($(rand_read_part 1 "latency-p95") | .valueQuantity.value), ($(rand_read_part 1 "latency-p99") | .valueQuantity.value), ($(rand_read_part 1 "latency-max") | .valueQuantity.value)] | . == sort")" "true"
test "fsync-rate positive" "$(echo "$output" | jq "$(param_expr "fsync-rate") | .valueQuantity.value > 0")" "true"
test "fsync-latency-p50 positive" "$(echo "$output" | jq "$(param_expr "fsync-latency-p50") | .valueQuantity.value > 0")" "true"
test_regex "direct-io" "$(echo "$output" | jq -r "$(param_expr "direct-io") | .valueBoolean")" "^(true|false)$"
test "processing-duration positive" "$(echo "$output" | jq "$(param_expr "processing-duration") | .valueQuantity.value > 0")" "true"
test "processing-duration unit code" "$(echo "$output" | jq -r "$(param_expr "processing-duration") | .valueQuantity.code")" "s"

# the underlying job is available via the admin API and carries the same
# results as Task outputs
job=$(curl -sfH 'Accept: application/fhir+json' "$base/__admin/Task?_profile=https://blaze-server.org/fhir/StructureDefinition/DiskPerfJob&_sort=-_lastUpdated&_count=1" | jq '.entry[0].resource')

# the disk-perf job type never existed in IG version 0.1.0, so it carries only
# the current canonical
test "job profile count" "$(echo "$job" | jq -r '.meta.profile | length')" "1"
test "job profile URL" "$(echo "$job" | jq -r '.meta.profile[0]')" "https://blaze-server.org/fhir/StructureDefinition/DiskPerfJob"
test "job status" "$(echo "$job" | jq -r '.status')" "completed"

output_uri="https://blaze-server.org/fhir/CodeSystem/DiskPerfJobOutput"

output_expr() {
  echo ".output[] | select(.type.coding[] | select(.system == \"$output_uri\" and .code == \"$1\"))"
}

concurrency_url="https://blaze-server.org/fhir/StructureDefinition/disk-perf-concurrency"

# selects the output with code $1 of the read run with concurrency $2
read_output_expr() {
  echo "$(output_expr "$1") | select(any(.extension[]; .url == \"$concurrency_url\" and .valuePositiveInt == $2))"
}

test "job score range" "$(echo "$job" | jq "$(output_expr "score") | .valueDecimal >= 0 and .valueDecimal <= 100")" "true"
test_regex "job rating" "$(echo "$job" | jq -r "$(output_expr "rating") | .valueCode")" "^(excellent|good|acceptable|insufficient)$"
test "job seq-write-throughput positive" "$(echo "$job" | jq "$(output_expr "seq-write-throughput") | .valueQuantity.value > 0")" "true"
test "job seq-write-throughput unit code" "$(echo "$job" | jq -r "$(output_expr "seq-write-throughput") | .valueQuantity.code")" "By/s"
test "job read-iops sweep concurrencies" "$(echo "$job" | jq -r "[$(output_expr "read-iops") | .extension[] | select(.url == \"$concurrency_url\") | .valuePositiveInt] | join(\" \")")" "1 2 4 8 16 32"
test "job read-iops at 1 reader positive" "$(echo "$job" | jq "$(read_output_expr "read-iops" 1) | .valueQuantity.value > 0")" "true"
test "job read-throughput at 32 readers positive" "$(echo "$job" | jq "$(read_output_expr "read-throughput" 32) | .valueQuantity.value > 0")" "true"
test "job read-latency-p50 unit code" "$(echo "$job" | jq -r "$(read_output_expr "read-latency-p50" 1) | .valueQuantity.code")" "us"
test "job read-latency percentiles monotone" "$(echo "$job" | jq "[($(read_output_expr "read-latency-p50" 1) | .valueQuantity.value), ($(read_output_expr "read-latency-p95" 1) | .valueQuantity.value), ($(read_output_expr "read-latency-p99" 1) | .valueQuantity.value), ($(read_output_expr "read-latency-max" 1) | .valueQuantity.value)] | . == sort")" "true"
test "job fsync-rate positive" "$(echo "$job" | jq "$(output_expr "fsync-rate") | .valueQuantity.value > 0")" "true"
test "job fsync-latency-p50 positive" "$(echo "$job" | jq "$(output_expr "fsync-latency-p50") | .valueQuantity.value > 0")" "true"
test_regex "job direct-io" "$(echo "$job" | jq -r "$(output_expr "direct-io") | .valueBoolean")" "^(true|false)$"
test "job processing-duration positive" "$(echo "$job" | jq "$(output_expr "processing-duration") | .valueQuantity.value > 0")" "true"
test "job processing-duration unit code" "$(echo "$job" | jq -r "$(output_expr "processing-duration") | .valueQuantity.code")" "s"

# the job outputs carry the same score as the output parameters
test "job score output" "$(echo "$job" | jq "$(output_expr "score") | .valueDecimal")" "$(echo "$output" | jq "$(param_expr "score") | .valueDecimal")"

# the phase outputs are removed on completion
test_empty "current-phase output" "$(echo "$job" | jq -r "$(output_expr "current-phase") | .valueCode" 2>/dev/null || true)"
