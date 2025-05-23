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

evaluate_measure() {
  parameters "$2" | curl -sH "Content-Type: application/fhir+json" -d @- "$1/Measure/\$evaluate-measure"
}

fetch_patients() {
  curl -s "$1/Patient?_list=$2&_count=200"
}

BASE="http://localhost:8080/fhir"
NAME=$1
EXPECTED_COUNT=$2

MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$MEASURE_URI" "$NAME" | transact "$BASE" > /dev/null

REPORT=$(evaluate_measure "$BASE" "$MEASURE_URI")
COUNT=$(echo "$REPORT" | jq -r ".group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "✅ count ($COUNT) equals the expected count"
else
  echo "🆘 count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$REPORT" | jq .
  exit 1
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
