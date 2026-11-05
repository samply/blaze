#!/usr/bin/env bash

set -euo pipefail

server="${BLAZE_SERVER:-http://localhost:8080/fhir}"
type="${1:-Encounter}"
query="${2:-_count=1000}"
format="${3:-xml}"
runs="${4:-5}"
out="${5:-profiling/xml-paging/results.csv}"

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
  echo "timestamp,format,type,query,run,pages,entries,bytes,seconds" > "$out"
fi

extract_json_next() {
  jq -r '.link[]? | select(.relation == "next") | .url' "$1" | head -1
}

extract_xml_next() {
  xmllint --xpath "string(/*[local-name()='Bundle']/*[local-name()='link'][*[local-name()='relation']/@value='next']/*[local-name()='url']/@value)" "$1" 2>/dev/null || true
}

count_json_entries() {
  jq -r '.entry | length // 0' "$1"
}

count_xml_entries() {
  xmllint --xpath "count(/*[local-name()='Bundle']/*[local-name()='entry'])" "$1" 2>/dev/null || echo 0
}

url="${server}/${type}?${query}"

for run in $(seq 1 "$runs"); do
  tmp_dir="$(mktemp -d)"
  page_file="${tmp_dir}/page"
  pages=0
  entries=0
  bytes=0
  next="$url"
  start_ns="$(date +%s%N)"

  while [[ -n "$next" ]]; do
    curl -fsS -H "Accept: ${accept}" "$next" -o "$page_file"
    page_bytes="$(wc -c < "$page_file" | tr -d ' ')"
    bytes=$((bytes + page_bytes))
    pages=$((pages + 1))

    if [[ "$format" == "json" ]]; then
      page_entries="$(count_json_entries "$page_file")"
      next="$(extract_json_next "$page_file")"
    else
      page_entries="$(count_xml_entries "$page_file")"
      next="$(extract_xml_next "$page_file")"
    fi

    entries=$((entries + page_entries))
  done

  end_ns="$(date +%s%N)"
  seconds="$(awk -v start="$start_ns" -v end="$end_ns" 'BEGIN { printf "%.3f", (end - start) / 1000000000 }')"
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$timestamp" "$format" "$type" "$query" "$run" "$pages" "$entries" "$bytes" "$seconds" | tee -a "$out"
  rm -rf "$tmp_dir"
done
