#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
HEADERS=$(curl -sfXDELETE -D - "$BASE/Patient/$PATIENT_ID")

test_empty "content type header" "$(echo "$HEADERS" | grep -i content-type | tr -d '\r')"

PATIENT_HISTORY=$(curl -s "$BASE/Patient/$PATIENT_ID/_history")

TOTAL=$(echo "$PATIENT_HISTORY" | jq .total)
if [ "$TOTAL" = "1" ]; then
  echo "✅ patient history has one entry"
else
  echo "🆘 patient history has $TOTAL entries"
  exit 1
fi

METHOD=$(echo "$PATIENT_HISTORY" | jq -r .entry[].request.method)
if [ "$METHOD" = "DELETE" ]; then
  echo "✅ patient history entry has method DELETE"
else
  echo "🆘 patient history entry has method $METHOD"
  exit 1
fi

STATUS=$(echo "$PATIENT_HISTORY" | jq -r .entry[].response.status)
if [ "$STATUS" = "204" ]; then
  echo "✅ patient history entry has status 204"
else
  echo "🆘 patient history entry has status $STATUS"
  exit 1
fi

PATIENT_STATUS=$(curl -is "$BASE/Patient/$PATIENT_ID" -o /dev/null -w '%{response_code}')
if [ "$PATIENT_STATUS" = "410" ]; then
  echo "✅ patient status is HTTP/1.1 410 Gone"
else
  echo "🆘 patient status is $PATIENT_STATUS"
  exit 1
fi

PATIENT_OUTCOME=$(curl -s "$BASE/Patient/$PATIENT_ID")

CODE=$(echo "$PATIENT_OUTCOME" | jq -r .issue[].code)
if [ "$CODE" = "deleted" ]; then
  echo "✅ patient outcome has code deleted"
else
  echo "🆘 patient outcome has code $CODE"
  exit 1
fi
