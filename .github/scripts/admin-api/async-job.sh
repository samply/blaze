#!/bin/bash -e

# issues an async request and inspects the resulting job

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

if [[ "$OSTYPE" == "darwin"* ]]; then
  DATE_CMD="gdate"
else
  DATE_CMD="date"
fi

base="http://localhost:8080/fhir"
headers=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$base/Observation?code=http://loinc.org|8310-5&_summary=count")
job_id=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d '/' -f6)

# wait to fetch the completed job
sleep 1
job=$(curl -s -H 'Accept: application/fhir+json' "$base/__admin/Task/$job_id")

test "profile URL" "$(echo "$job" | jq -r '.meta.profile[]')" "https://samply.github.io/blaze/fhir/StructureDefinition/AsyncInteractionJob"
test "status" "$(echo "$job" | jq -r '.status')" "completed"

authored_on_iso=$(echo "$job" | jq -r '.authoredOn')
authored_on_epoch_seconds=$($DATE_CMD -d "$authored_on_iso" +%s)
now_epoch_seconds=$($DATE_CMD +%s)
if ((now_epoch_seconds - authored_on_epoch_seconds < 10)); then
  echo "✅ the authoredOn dateTime is set and current"
else
  echo "🆘 the authoredOn dateTime is $authored_on_iso, but should be a current dateTime"
  exit 1
fi

parameter_uri="https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter"

input_expr() {
  echo ".input[] | select(.type.coding[] | select(.system == \"$parameter_uri\" and .code == \"$1\"))"
}

test "t" "$(echo "$job" | jq -r "$(input_expr "t") | .valueUnsignedInt")" "1"

output_uri="https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobOutput"

output_expr() {
  echo ".output[] | select(.type.coding[] | select(.system == \"$output_uri\" and .code == \"$1\"))"
}

processing_duration="$(echo "$job" | jq "$(output_expr "processing-duration") | .valueQuantity")"
test "processing-duration unit system" "$(echo "$processing_duration" | jq -r .system)" "http://unitsofmeasure.org"
test "processing-duration unit code" "$(echo "$processing_duration" | jq -r .code)" "s"

# History
job_history=$(curl -s -H 'Accept: application/fhir+json' "$base/__admin/Task/$job_id/_history")

test "history resource type" "$(echo "$job_history" | jq -r '.resourceType')" "Bundle"
test "history bundle type" "$(echo "$job_history" | jq -r '.type')" "history"
test "history total" "$(echo "$job_history" | jq -r '.total')" "3"
test "history 0 status" "$(echo "$job_history" | jq -r '.entry[0].resource.status')" "completed"
test "history 1 status" "$(echo "$job_history" | jq -r '.entry[1].resource.status')" "in-progress"
test "history 2 status" "$(echo "$job_history" | jq -r '.entry[2].resource.status')" "ready"
