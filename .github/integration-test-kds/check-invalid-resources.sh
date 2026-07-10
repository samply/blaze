#!/bin/bash
set -euo pipefail

# Checks the resources that the external validator flagged as invalid while the
# data was loaded into the data server.
#
# With the default failure mode `tag-outcome` every invalid resource carries:
#   * a meta tag with system
#     `https://blaze-server.org/fhir/CodeSystem/ValidationStatus` and code
#     `invalid`,
#   * a meta extension referencing the contained OperationOutcome, and
#   * a contained OperationOutcome — located by following that extension's
#     reference, its id is opaque — with at least one issue of severity `error`
#     or `fatal`.
#
# Usage: check-invalid-resources.sh <expected-count>

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
tag_system="https://blaze-server.org/fhir/CodeSystem/ValidationStatus"
outcome_ext="https://blaze-server.org/fhir/StructureDefinition/validation-outcome"

expected_count="$1"

# Gather all invalid resources across all resource types via the system-wide
# `_tag` search into a JSON stream (one resource per line). blazectl handles
# paging.
invalid=$(blazectl --server "$base" download -q "_tag=${tag_system}|invalid" 2>/dev/null)

# --- assert the number of invalid resources ---------------------------------

count=$(echo "$invalid" | jq -s 'length')
test "number of invalid resources" "$count" "$expected_count"

echo "ℹ️ invalid resources by type:"
echo "$invalid" | jq -rs 'group_by(.resourceType)[] | "  \(length) \(.[0].resourceType)"'

# --- assert every invalid resource has a proper validation OperationOutcome --

malformed=$(echo "$invalid" | jq -rs --arg sys "$tag_system" --arg ext "$outcome_ext" '
  [.[] | select(
     ([.meta.tag[]? | select(.system == $sys and .code == "invalid")] | length == 0)
     or ([.meta.extension[]? | select(.url == $ext)] | length == 0)
     # Follow the meta extension reference (e.g. "#<id>") to the contained
     # OperationOutcome instead of assuming a fixed id, and require it to carry
     # an error/fatal issue.
     or (([.meta.extension[]? | select(.url == $ext) | .valueReference.reference // "" | ltrimstr("#")] as $ids
          | [.contained[]? | select(.resourceType == "OperationOutcome" and (.id as $cid | $ids | index($cid)))
             | .issue[]? | select(.severity == "error" or .severity == "fatal")] | length == 0)))
   | "\(.resourceType)/\(.id)"] | join(", ")')

test_empty "set of invalid resources without a proper validation OperationOutcome" "$malformed"

# --- show the OperationOutcome of some invalid resources ---------------------

# The output is truncated inside jq because a downstream `head` would close the
# pipe early and kill jq with SIGPIPE, failing the script under `pipefail`.
echo "ℹ️ validation issues of the invalid resources:"
echo "$invalid" | jq -rs '
  [.[] | "  \(.resourceType)/\(.id):",
    (.contained[]? | select(.resourceType == "OperationOutcome")
     | .issue[] | select(.severity == "error" or .severity == "fatal")
     | "    [\(.severity)] \(.details.text // .diagnostics // (.code | tostring))")]
  | .[:60][]'
