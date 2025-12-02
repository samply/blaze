#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../../.github/scripts/util.sh"

ca_cert="$script_dir/../ingress/blaze-cert.pem"

base="https://blaze.localhost/fhir"
access_token="$("$script_dir/fetch-access-token.sh")"
expected_size=$(curl -s --cacert "$ca_cert" --oauth2-bearer "$access_token" "$base/Patient?_summary=count" | jq -r .total)
actual_size=$(blazectl --server "$base" \
  --certificate-authority "$ca_cert" \
  --token "$access_token" \
  download Patient -q "_count=10" 2>/dev/null | wc -l | xargs)

test "download size" "$actual_size" "$expected_size"
