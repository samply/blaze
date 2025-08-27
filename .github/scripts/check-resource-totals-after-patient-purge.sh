#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
ACTUAL_TOTALS="$(curl -sH 'Accept: application/fhir+json' "$BASE/\$totals" | jq -r '.parameter[] | [.name, .valueUnsignedInt] | @csv')"
EXPECTED_TOTALS="$(cat <<END
"Location",194
"Medication",326
"Organization",194
"Practitioner",195
"PractitionerRole",195
"StructureDefinition",204
END
)"

test "resource totals" "$ACTUAL_TOTALS" "$EXPECTED_TOTALS"
