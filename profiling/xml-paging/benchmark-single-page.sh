#!/usr/bin/env bash

set -euo pipefail

server="${BLAZE_SERVER:-http://localhost:8080/fhir}"
type="${1:-Encounter}"
query="${2:-_count=1000}"
format="${3:-xml}"
runs="${4:-20}"
out="${5:-profiling/xml-paging/results-single-page.csv}"

case "$format" in
  json)
    accept="application/fhir+json"
    ;;
  xml)
    accept="application/fhir+xml"
    ;;
  *)
    echo "Format must be either json or xml, got: $format" >&2
    exit 1
    ;;
esac

mkdir -p "$(dirname "$out")"
if [[ ! -f "$out" ]]; then
  echo "timestamp,format,type,query,run,bytes,seconds" > "$out"
fi

url="${server}/${type}?${query}"

for run in $(seq 1 "$runs"); do
  tmp_file="$(mktemp)"
  seconds="$(curl -fsS -H "Accept: ${accept}" -o "$tmp_file" -w '%{time_total}' "$url")"
  bytes="$(wc -c < "$tmp_file" | tr -d ' ')"
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '%s,%s,%s,%s,%s,%s,%.3f\n' \
    "$timestamp" "$format" "$type" "$query" "$run" "$bytes" "$seconds" | tee -a "$out"
  rm -f "$tmp_file"
done
