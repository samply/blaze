#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

create "$BASE/GraphDefinition" < "$SCRIPT_DIR/graph/GraphDefinition-patient-observation-encounter.json" > /dev/null

PATIENT_IDENTIFIER="X79746011X"
PATIENT_ID=$(curl -sH 'Accept: application/fhir+json' "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')
echo "$PATIENT_ID"
BUNDLE=$(curl -sH 'Accept: application/fhir+json' "$BASE/Patient/$PATIENT_ID/\$graph?graph=patient-observation-encounter")
ACTUAL_SIZE=$(echo "$BUNDLE" | jq -r .total)
IDS="$(echo "$BUNDLE" | jq -r '.entry[].resource.id')"

test "size" "$ACTUAL_SIZE" "150"

test "no duplicates" "$(echo "$IDS" | sort -u | wc -l | xargs)" "$(echo "$IDS" | wc -l | xargs)"

test "type counts" "$(echo "$BUNDLE" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$SCRIPT_DIR/graph/$PATIENT_IDENTIFIER-patient-observation-encounter-type-counts.csv")"
