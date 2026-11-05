#!/bin/bash
set -euo pipefail

#
# This script purges all patients, four at a time, and checks the outcome of
# each request.
#
# The purge of the 8 patients referenced by a Device has to fail, because the
# Device isn't part of the Patient compartment in R4, so the referential
# integrity would be violated. All other purges have to succeed.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
out_dir="$(mktemp -d)"
trap 'rm -rf "$out_dir"' EXIT

# Purges all patients, writing the response bodies to the files
# $out_dir/<id>.json and one line with the id and the response code per
# patient to the file $out_dir/codes.
blazectl --server "$base" download Patient -q '_elements=id' --count 1000 2>/dev/null | \
  jq -r '.id' | \
  xargs -P 4 -I {} curl -s -XPOST -H 'Accept: application/fhir+json' -o "$out_dir/{}.json" \
    -w '{} %{response_code}\n' "$base/Patient/{}/\$purge" > "$out_dir/codes"

# Classifies the outcome of the purge of the patient with id $1 and response
# code $2.
outcome() {
  local id="$1"
  local code="$2"
  local diagnostics
  diagnostics="$(jq -r '.issue[0].diagnostics' "$out_dir/$id.json")"
  local device_conflict="^Referential integrity violated\. Resource \`Patient/$id\` should be deleted but is referenced from \`Device/[^\`]+\`( and others)?\.$"

  if [ "$code" = "200" ] && [ "$(jq -r '.issue[0].code' "$out_dir/$id.json")" = "success" ]; then
    echo "success"
  elif [ "$code" = "409" ] && [[ "$diagnostics" =~ $device_conflict ]]; then
    echo "device-conflict"
  else
    echo "unexpected $code: $diagnostics"
  fi
}

outcomes="$(while read -r id code; do outcome "$id" "$code"; done < "$out_dir/codes" | sort | uniq -c | awk '{$1=$1; print}')"
expected_outcomes="$(cat <<END
8 device-conflict
112 success
END
)"

test "purge outcomes" "$outcomes" "$expected_outcomes"
