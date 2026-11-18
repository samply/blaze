#!/bin/bash
set -euo pipefail

#
# This script purges all patients, four at a time, and checks the outcome of
# each request.
#
# A Provenance isn't purged, because it can target several patients. The one
# Synthea creates for each patient targets only that patient, so it's deleted
# before the purge, which would fail otherwise.
#
# The purge of the 8 patients referenced by a Device has to fail, because the
# purge GraphDefinition doesn't select Devices, so the referential integrity
# would be violated. All other purges have to succeed.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
out_dir="$(mktemp -d)"
trap 'rm -rf "$out_dir"' EXIT

patient_ids="$(blazectl -sS --server "$base" download Patient -q '_elements=id' --count 1000 | jq -r '.id')"

echo "$patient_ids" | \
  xargs -P 4 -I {} curl -sS -H 'Accept: application/fhir+json' -X DELETE -o /dev/null \
    -w '%{response_code}\n' "$base/Provenance?patient={}" > "$out_dir/provenance-delete-codes"

test "Provenance delete response codes" "$(sort "$out_dir/provenance-delete-codes" | uniq -c | awk '{$1=$1; print}')" "120 204"

# Purges all patients, writing the response bodies to the files
# $out_dir/<id>.json and one line with the id and the response code per
# patient to the file $out_dir/purge-codes.
echo "$patient_ids" | \
  xargs -P 4 -I {} curl -sS -H 'Accept: application/fhir+json' -X POST -o "$out_dir/{}.json" \
    -w '{} %{response_code}\n' "$base/Patient/{}/\$purge" > "$out_dir/purge-codes"

# Classifies the outcome of the purge of the patient with id $1 and response
# code $2.
outcome() {
  local id="$1"
  local code="$2"
  local diagnostics
  diagnostics="$(jq -r '.issue[0].diagnostics' "$out_dir/$id.json")"
  local device_ref="\`Device/[^\`]+\`"
  local device_conflict="^Referential integrity violated\. Resource \`Patient/$id\` should be deleted but is referenced from $device_ref(, $device_ref)*( and others)?\.$"

  if [ "$code" = "200" ] && [ "$(jq -r '.issue[0].code' "$out_dir/$id.json")" = "success" ]; then
    echo "success"
  elif [ "$code" = "409" ] && [[ "$diagnostics" =~ $device_conflict ]]; then
    echo "device-conflict"
  else
    echo "unexpected $code: $diagnostics"
  fi
}

outcomes="$(while read -r id code; do outcome "$id" "$code"; done < "$out_dir/purge-codes" | sort | uniq -c | awk '{$1=$1; print}')"
expected_outcomes="$(cat <<END
8 device-conflict
112 success
END
)"

test "purge outcomes" "$outcomes" "$expected_outcomes"
