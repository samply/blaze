#!/bin/bash -e

FILENAME=$1
BASE="http://localhost:8080/fhir"

RESOURCE_TYPE="$(jq -r .resourceType "$FILENAME")"
RESOURCE_ID="$(jq -r .id "$FILENAME")"
HTTP_METHOD="PUT"
if [[ "$RESOURCE_TYPE" =~ Bundle|Condition|Composition|Consent|Device|DeviceMetric|DiagnosticReport|DocumentReference|Encounter|EvidenceVariable|FamilyMemberHistory|Group|ImagingStudy|Library|List|Media|Medication|MedicationAdministration|MedicationRequest|MedicationStatement|Observation|Organization|Patient|Practitioner|PractitionerRole|Procedure|ResearchStudy|ResearchSubject|ServiceRequest|Specimen|Substance|Task ]]; then
  URL_PATH="$BASE/$RESOURCE_TYPE"
  if [ ! -z "$RESOURCE_ID" ]; then
    URL_PATH="$URL_PATH/$RESOURCE_ID"
  fi
  if [[ "$RESOURCE_TYPE" =~ Bundle ]]; then
    URL_PATH="$BASE"
    HTTP_METHOD="POST"
  fi
  echo -e "\nUpload $FILENAME"
  curl -s  -X "$HTTP_METHOD" -H "Content-Type: application/fhir+json" -H "Prefer: return=minimal" -d @"$FILENAME" "$URL_PATH"
fi
