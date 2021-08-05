#!/bin/bash -e

#
# This script creates and reads a patient in a single transaction.
#

PATIENT_ID="e42a47bb-a371-4cf5-9f17-51e59c1f612a"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "PUT",
        "url": "Patient/$PATIENT_ID"
      },
      "resource": {
        "resourceType": "Patient",
        "id": "$PATIENT_ID"
      }
    },
    {
      "request": {
        "method": "GET",
        "url": "Patient/$PATIENT_ID"
      }
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" \
  "http://localhost:8080/fhir")

RESOURCE_TYPE="$(echo "$RESULT" | jq -r .resourceType)"
if [ "$RESOURCE_TYPE" = "Bundle" ]; then
  echo "OK: the resource type is Bundle"
else
  echo "Fail: the resource type is $RESOURCE_TYPE, expected Bundle"
  exit 1
fi

BUNDLE_TYPE="$(echo "$RESULT" | jq -r .type)"
if [ "$BUNDLE_TYPE" = "transaction-response" ]; then
  echo "OK: the bundle type is transaction-response"
else
  echo "Fail: the bundle type is $BUNDLE_TYPE, expected transaction-response"
  exit 1
fi

RESPONSE_STATUS="$(echo "$RESULT" | jq -r .entry[1].response.status)"
if [ "$RESPONSE_STATUS" = "200" ]; then
  echo "OK: the response status is 200"
else
  echo "Fail: the response status is $RESPONSE_STATUS, expected 200"
  exit 1
fi

RESPONSE_PATIENT_ID="$(echo "$RESULT" | jq -r .entry[1].resource.id)"
if [ "$RESPONSE_PATIENT_ID" = "$PATIENT_ID" ]; then
  echo "OK: patient id's match"
else
  echo "Fail: response patient id was $RESPONSE_PATIENT_ID but should be $RESPONSE_PATIENT_ID"
  exit 1
fi
