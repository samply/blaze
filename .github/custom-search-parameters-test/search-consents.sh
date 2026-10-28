#!/bin/bash
set -euo pipefail

# searches for Consents with a nested provision with a code and a period
# matching the date search value $1 and expects $2 Consents to be found

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
code="urn:oid:2.16.840.1.113883.3.1937.777.24.5.3|2.16.840.1.113883.3.1937.777.24.5.3.8"

count() {
  search_strict "$base/Consent?provision-provision-code-period=$code\$$1&_summary=count" | jq .total
}

test "number of Consents with period $1" "$(count "$1")" "$2"
