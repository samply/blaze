#!/bin/bash
set -euo pipefail

#
# This script sends one large batch bundle and checks that every entry is
# processed and that the response entries keep the order of the request
# entries.
#
# The entries of a batch bundle are processed concurrently with a sliding
# window. So the bundle interleaves writes with reads, because the reads are
# much faster than the writes and make the entries complete out of order. The
# response entries have to keep the bundle order nonetheless.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# the bundle holds one read after each write, so it has twice as many entries
num_writes="${1:-2000}"
num_entries=$((num_writes * 2))

prefix="large-batch"

# tag marking the patients written by this script, so that all of them can be
# counted with a single search
tag_system="http://acme.org/codes"
tag_code="$prefix"

bundle() {
  jq -nc --arg prefix "$prefix" --arg system "$tag_system" --arg code "$tag_code" \
    --argjson num "$num_entries" '
    {
      resourceType: "Bundle",
      type: "batch",
      entry: [
        range($num) |
        if . % 2 == 0 then
          {
            resource: {
              resourceType: "Patient",
              id: "\($prefix)-\(.)",
              meta: {tag: [{system: $system, code: $code}]}
            },
            request: {method: "PUT", url: "Patient/\($prefix)-\(.)"}
          }
        else
          {request: {method: "GET", url: "Patient?_summary=count"}}
        end
      ]
    }'
}

start="$(date +%s)"
result="$(bundle | transact "$base")"
echo "ℹ️ processed $num_entries batch bundle entries in $(($(date +%s) - start)) s"

test "resource type" "$(echo "$result" | jq -r '.resourceType')" "Bundle"
test "bundle type" "$(echo "$result" | jq -r '.type')" "batch-response"
test "number of response entries" "$(echo "$result" | jq -r '.entry | length')" "$num_entries"

test "distinct statuses of the write entries" \
  "$(echo "$result" | jq -r '[.entry | to_entries[] | select(.key % 2 == 0) | .value.response.status] | unique | join(",")')" \
  "201"

test "distinct statuses of the read entries" \
  "$(echo "$result" | jq -r '[.entry | to_entries[] | select(.key % 2 == 1) | .value.response.status] | unique | join(",")')" \
  "200"

# every write entry has to answer with the location of the patient that the
# request entry at that very position created
test "number of write entries at the wrong position" \
  "$(echo "$result" | jq -r --arg prefix "$prefix" '
      [.entry | to_entries[]
       | select(.key % 2 == 0)
       | select((.value.response.location // "" | split("/") | .[5]) != "\($prefix)-\(.key)")]
      | length')" \
  "0"

test "number of created patients" \
  "$(search_strict "$base/Patient?_tag=$tag_system|$tag_code&_summary=count" | jq -r '.total')" \
  "$num_writes"
