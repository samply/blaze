#!/bin/bash
set -euo pipefail

# Checks that contained resources of different types are preserved.
#
# The Synthea ExplanationOfBenefit resources are the only resources of the test
# data with contained resources. Each of them contains both a ServiceRequest and
# a Coverage. Writing every contained resource with the type of the first one
# would turn the Coverage into a ServiceRequest and drop all fields that don't
# exist on ServiceRequest.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

eob=$(blazectl --server "$base" download ExplanationOfBenefit -q '_count=1000' 2>/dev/null)

# Without resources the checks below would hold trivially. The expected total is
# asserted by check-resource-totals.sh.
test_non_empty "ExplanationOfBenefit download" "$eob"

echo "ℹ️ checking $(echo "$eob" | jq -s 'length') ExplanationOfBenefit resources"

# --- assert the types of the contained resources are preserved --------------

malformed=$(echo "$eob" | jq -rs '
  [.[] | select(
     ([.contained[]? | select(.resourceType == "ServiceRequest")] | length != 1)
     or ([.contained[]? | select(.resourceType == "Coverage")] | length != 1))
   | .id] | join(", ")')

test_empty "set of ExplanationOfBenefit resources not containing one ServiceRequest and one Coverage" "$malformed"

# --- assert the fields of the contained Coverage are preserved --------------

# These fields don't exist on ServiceRequest and would be lost if the Coverage
# was written as one.
incomplete=$(echo "$eob" | jq -rs '
  [.[] | . as $r
   | ($r.contained[] | select(.resourceType == "Coverage")) as $coverage
   | select(($coverage.type == null) or ($coverage.beneficiary == null)
            or ($coverage.payor == null))
   | $r.id] | join(", ")')

test_empty "set of ExplanationOfBenefit resources with an incomplete contained Coverage" "$incomplete"
