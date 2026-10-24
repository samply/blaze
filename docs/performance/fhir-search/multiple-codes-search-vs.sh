#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local name="$1"
  local vcl_url="$(echo "http://fhir.org/VCL?v1=$2" | jq -Rr @uri)"

  echo "Counting $name Observations..."
  count-resources-raw "$base" "Observation" "code:in=$vcl_url"
}

download-resources() {
  local name="$1"
  local vcl_url="$(echo "http://fhir.org/VCL?v1=$2" | jq -Rr @uri)"

  echo "Downloading $name Observations..."
  download-resources-raw "$base" "Observation" "code:in=$vcl_url"
}

restart "$compose_file"
name="10-observation-codes"
vcl_expr="(http://loinc.org)($(paste -sd';' "$script_dir/observation-codes-10.txt"))"
profile "$compose_file" "count-$name" count-resources "$name" "$vcl_expr"
profile "$compose_file" "download-$name" download-resources "$name" "$vcl_expr"

restart "$compose_file"
name="100-observation-codes"
vcl_expr="(http://loinc.org)($(paste -sd';' "$script_dir/observation-codes-100.txt"))"
profile "$compose_file" "count-$name" count-resources "$name" "$vcl_expr"
profile "$compose_file" "download-$name" download-resources "$name" "$vcl_expr"
