#!/bin/bash

# Checks that every metric charted by the Grafana dashboard is actually exported
# by Blaze. The failure mode this guards against is a metric being renamed or
# removed in the code while the dashboard keeps charting the old name.
#
# The check is one-directional: it flags dashboard metrics Blaze does not
# export, not exported metrics that have no panel. Plenty of metrics are
# intentionally not charted.

set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

dashboard="${1:-modules/monitoring/target/blaze-dashboard.json}"
url="${2:-http://localhost:8081/metrics}"

# Metrics the dashboard charts that this scrape can't be expected to contain.
# A label-parameterised metric exports no samples until its first use, so a
# metric the integration test never exercises is absent even though the
# dashboard is correct.
allowlist=(
  # The CQL expression cache is disabled in the integration test, so the
  # namespace defining these is never used. Exercised by setting
  # CQL_EXPR_CACHE_SIZE and running a CQL query.
  "blaze_cql_expr_cache_bloom_filter_bytes_bucket"
  "blaze_cql_expr_cache_bloom_filter_creation_duration_seconds_bucket"
  "blaze_cql_expr_cache_bloom_filter_false_positive_total"
  "blaze_cql_expr_cache_bloom_filter_not_useful_total"
  "blaze_cql_expr_cache_bloom_filter_useful_total"

  # Labelled with subject_type, so it only appears after the first
  # $evaluate-measure call. The integration test doesn't evaluate measures;
  # the cql-test job does.
  "fhir_evaluate_measure_evaluate_duration_seconds_bucket"

  # Blaze doesn't export this metric at all. The "Write Timeouts" panel charts
  # the RocksDB WRITE_TIMEDOUT ticker, which the RocksDB stats collector in
  # blaze.db.kv.rocksdb.metrics never registered.
  "blaze_rocksdb_write_timeout_total"
)

# Extracts the metric names from the PromQL expressions of all panels. Label
# matchers, ranges and grouping clauses are stripped first, then every
# identifier directly followed by an opening parenthesis - a PromQL function -
# is dropped. What remains are the metric names.
metric-names() {
  jq -r '
    [.panels[].panels[]?.targets[]?.expr]
    | map(gsub("\\{[^}]*\\}"; " ")
        | gsub("\\[[^\\]]*\\]"; " ")
        | gsub("\\b(by|without|on|ignoring|group_left|group_right)\\s*\\([^)]*\\)"; " ")
        | gsub("[a-zA-Z_:][a-zA-Z0-9_:]*\\s*\\("; " "))
    | map(scan("[a-zA-Z_:][a-zA-Z0-9_:]*"))
    | flatten | unique | .[]' "$1"
}

allowlisted() {
  [[ " ${allowlist[*]} " == *" $1 "* ]]
}

metrics="$(curl -sf "$url")"
test_non_empty "metrics scrape of $url" "$metrics"

checked=0
missing=()
while read -r name; do
  if allowlisted "$name"; then
    continue
  fi
  checked=$((checked + 1))
  if ! grep -qF -- "$name" <<< "$metrics"; then
    missing+=("$name")
  fi
done < <(metric-names "$dashboard")

echo "ℹ️  checked $checked dashboard metrics against $url"

test_non_empty "number of checked dashboard metrics" "$checked"
test_empty "list of dashboard metrics not exported by Blaze" "${missing[*]-}"
