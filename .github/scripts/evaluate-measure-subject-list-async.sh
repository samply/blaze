#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"
. "$SCRIPT_DIR/evaluate-measure-util.sh"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "periodStart",
      "valueDate": "2000"
    },
    {
      "name": "periodEnd",
      "valueDate": "2030"
    },
    {
      "name": "reportType",
      "valueCode": "subject-list"
    },
    {
      "name": "measure",
      "valueString": "urn:uuid:$1"
    }
  ]
}
END
}

START_EPOCH="$(date +"%s")"

eclipsed() {
  EPOCH="$(date +"%s")"
  echo $((EPOCH - START_EPOCH))
}

evaluate_measure() {
  parameters "$2" | curl -s -H "Prefer: respond-async,return=representation" -H "Content-Type: application/fhir+json" -d @- -o /dev/null -D - "$1/Measure/\$evaluate-measure"
}

fetch_patients() {
  curl -s "$1/Patient?_list=$2&_count=200"
}

BASE="http://localhost:8080/fhir"
NAME=$1
EXPECTED_COUNT=$2

MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$MEASURE_URI" "$NAME" | transact "$BASE" > /dev/null

HEADERS=$(evaluate_measure "$BASE" "$MEASURE_URI")
STATUS_URL=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$STATUS_URL")" != "200") ]]; do
  sleep 0.1
done

BUNDLE="$(curl -s -H 'Accept: application/fhir+json' "$STATUS_URL")"
REPORT=$(echo "$BUNDLE" | jq -r ".entry[0].resource")
COUNT=$(echo "$REPORT" | jq -r ".group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "✅ count ($COUNT) equals the expected count"
else
  echo "🆘 count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$REPORT" | jq .
  exit 1
fi

if [ "0" = "$EXPECTED_COUNT" ]; then
  exit 0
fi

LIST_ID=$(echo "$REPORT" | jq -r '.group[0].population[0].subjectResults.reference | split("/")[1]')
PATIENT_BUNDLE=$(fetch_patients "$BASE" "$LIST_ID")
ID_COUNT=$(echo "$PATIENT_BUNDLE" | jq -r ".entry[].resource.id" | sort -u | wc -l | xargs)

if [ "$ID_COUNT" = "$EXPECTED_COUNT" ]; then
  echo "✅ downloaded patient count ($ID_COUNT) equals the expected count"
else
  echo "🆘 downloaded patient count ($ID_COUNT) != $EXPECTED_COUNT"
  echo "Patient bundle:"
  echo "$PATIENT_BUNDLE" | jq .
  exit 1
fi
