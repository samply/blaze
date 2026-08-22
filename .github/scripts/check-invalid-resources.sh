#!/bin/bash
set -euo pipefail

# Checks the resources that the external validator flagged as invalid while the
# data was loaded into the data server.
#
# Every invalid resource carries a meta tag with system
# `https://blaze-server.org/fhir/CodeSystem/ValidationStatus` and code
# `invalid`.
#
# With failure mode `tag-outcome` (the default) each invalid resource
# additionally carries:
#   * a meta extension referencing the contained OperationOutcome, and
#   * that contained OperationOutcome with at least one issue of severity
#     `error` or `fatal`.
#
# The id of the contained OperationOutcome is derived from its content and is
# deliberately opaque, so it is discovered by following the reference of the
# meta extension instead of assuming a well-known id.
#
# With failure mode `tag-only` the resource carries no such meta extension.
#
# Usage: check-invalid-resources.sh <expected-count> [tag-outcome|tag-only]

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
tag_system="https://blaze-server.org/fhir/CodeSystem/ValidationStatus"
outcome_ext="https://blaze-server.org/fhir/StructureDefinition/validation-outcome"

expected_count="$1"
mode="${2:-tag-outcome}"

# Gather all invalid resources across all resource types via the system-wide
# `_tag` search into a JSON stream (one resource per line). blazectl handles
# paging.
invalid=$(blazectl --server "$base" download -q "_tag=${tag_system}|invalid" 2>/dev/null)

# --- assert the number of invalid resources ---------------------------------

count=$(echo "$invalid" | jq -s 'length')
test "number of invalid resources" "$count" "$expected_count"

echo "ℹ️ invalid resources by type:"
echo "$invalid" | jq -rs 'group_by(.resourceType)[] | "  \(length) \(.[0].resourceType)"'

# --- assert every invalid resource has the expected shape -------------------

if [ "$mode" = "tag-only" ]; then
  # Every invalid resource must carry the invalid tag but no meta extension
  # referencing a contained validation OperationOutcome.
  malformed=$(echo "$invalid" | jq -rs --arg sys "$tag_system" --arg ext "$outcome_ext" '
    [.[] | select(
       ([.meta.tag[]? | select(.system == $sys and .code == "invalid")] | length == 0)
       or ([.meta.extension[]? | select(.url == $ext)] | length > 0))
     | "\(.resourceType)/\(.id)"] | join(", ")')

  test_empty "set of invalid resources not matching the tag-only shape" "$malformed"
else
  # Every invalid resource must carry the invalid tag and a meta extension whose
  # reference resolves to a contained OperationOutcome with an error or fatal
  # issue.
  malformed=$(echo "$invalid" | jq -rs --arg sys "$tag_system" --arg ext "$outcome_ext" '
    [.[] | . as $r
     | ([$r.meta.extension[]? | select(.url == $ext) | .valueReference.reference] | first) as $ref
     | (if $ref == null then null else ($ref | ltrimstr("#")) end) as $id
     | select(
         ([$r.meta.tag[]? | select(.system == $sys and .code == "invalid")] | length == 0)
         or ($id == null)
         or ([$r.contained[]? | select(.resourceType == "OperationOutcome" and .id == $id)] | length == 0)
         or ([$r.contained[]? | select(.resourceType == "OperationOutcome" and .id == $id)
              | .issue[]? | select(.severity == "error" or .severity == "fatal")] | length == 0))
     | "\($r.resourceType)/\($r.id)"] | join(", ")')

  test_empty "set of invalid resources without a proper validation OperationOutcome" "$malformed"

  # --- show the OperationOutcome of some invalid resources ------------------

  # The output is truncated inside jq because a downstream `head` would close
  # the pipe early and kill jq with SIGPIPE, failing the script under
  # `pipefail`.
  echo "ℹ️ validation issues of the invalid resources:"
  echo "$invalid" | jq -rs --arg ext "$outcome_ext" '
    [.[] | . as $r
     | ([$r.meta.extension[]? | select(.url == $ext) | .valueReference.reference] | first) as $ref
     | (if $ref == null then null else ($ref | ltrimstr("#")) end) as $id
     | "  \($r.resourceType)/\($r.id):",
       ($r.contained[]? | select(.resourceType == "OperationOutcome" and .id == $id)
        | .issue[]? | select(.severity == "error" or .severity == "fatal")
        | "    [\(.severity)] \(.details.text // .diagnostics // (.code | tostring))")]
    | .[:60][]'
fi
