#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"
. "$SCRIPT_DIR/evaluate-measure-util.sh"

bundle_evaluate_measure() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "Measure/\$evaluate-measure?measure=urn:uuid:$1&periodStart=2000&periodEnd=2030"
      }
    }
  ]
}
END
}

BASE="http://localhost:8080/fhir"
NAME="$1"
EXPECTED_COUNT="$2"

MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$MEASURE_URI" "$NAME" | transact "$BASE" > /dev/null

BUNDLE=$(bundle_evaluate_measure "$MEASURE_URI" | transact "$BASE")
COUNT=$(echo "$BUNDLE" | jq -r ".entry[0].resource.group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "OK 👍: count ($COUNT) equals the expected count"
else
  echo "Fail 😞: count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$BUNDLE" | jq .
  exit 1
fi
