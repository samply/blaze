#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
capability_statement=$(curl -sH 'Accept: application/fhir+json' "$base/metadata")

test "Patient Supported Profile" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Patient") .supportedProfile[0]')" "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient|2025.0.0"
