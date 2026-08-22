#!/bin/bash
set -euo pipefail

# Checks the `tag-only` and `tag-outcome` failure modes of the external
# validator on two known resources: one that conforms to the profile it claims
# and one that does not.
#
# In both modes the invalid resource is persisted and carries a meta tag with
# system `https://blaze-server.org/fhir/CodeSystem/ValidationStatus` and code
# `invalid`. Only `tag-outcome` additionally stores the validator's
# OperationOutcome as a contained resource referenced from a meta extension.
#
# The id of the contained OperationOutcome is derived from its content and is
# deliberately opaque, so it is discovered by following the reference of the
# meta extension instead of assuming a well-known id.
#
# The valid resource is used to show that conforming resources are stored
# without any validation tag.
#
# Usage: check-tag-mode.sh <tag-outcome|tag-only>

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
tag_system="https://blaze-server.org/fhir/CodeSystem/ValidationStatus"
outcome_ext="https://blaze-server.org/fhir/StructureDefinition/validation-outcome"

mode="$1"

request_body=$(mktemp)

trap 'rm -f "$request_body"' EXIT

# Stores the Patient of the JSON file $1 under the id $2 and returns its stored
# representation.
store_patient() {
  jq --arg id "$2" '.id = $id' "$1" > "$request_body"
  curl -sf -XPUT -H 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' \
    -d @"$request_body" -o /dev/null "$base/Patient/$2"
  curl -sf -H 'Accept: application/fhir+json' "$base/Patient/$2"
}

# --- a valid resource is stored without a validation tag --------------------

valid=$(store_patient "$script_dir/valid-patient.json" "valid-patient")

test "number of validation tags of the valid Patient" \
  "$(echo "$valid" | jq --arg sys "$tag_system" '[.meta.tag[]? | select(.system == $sys)] | length')" "0"
test "number of validation outcome extensions of the valid Patient" \
  "$(echo "$valid" | jq --arg ext "$outcome_ext" '[.meta.extension[]? | select(.url == $ext)] | length')" "0"

# --- an invalid resource is stored with a validation tag --------------------

invalid=$(store_patient "$script_dir/invalid-patient.json" "invalid-patient")

test "number of invalid tags of the invalid Patient" \
  "$(echo "$invalid" | jq --arg sys "$tag_system" '[.meta.tag[]? | select(.system == $sys and .code == "invalid")] | length')" "1"

if [ "$mode" = "tag-only" ]; then
  test "number of validation outcome extensions of the invalid Patient" \
    "$(echo "$invalid" | jq --arg ext "$outcome_ext" '[.meta.extension[]? | select(.url == $ext)] | length')" "0"
else
  outcome_id=$(echo "$invalid" | jq -r --arg ext "$outcome_ext" \
    '([.meta.extension[]? | select(.url == $ext) | .valueReference.reference] | first // "") | ltrimstr("#")')

  test_non_empty "reference of the validation outcome extension of the invalid Patient" "$outcome_id"
  test_not_equal "number of error issues of the contained OperationOutcome of the invalid Patient" \
    "$(echo "$invalid" | jq --arg id "$outcome_id" '[.contained[]? | select(.resourceType == "OperationOutcome" and .id == $id) | .issue[]? | select(.severity == "error" or .severity == "fatal")] | length')" "0"
fi
