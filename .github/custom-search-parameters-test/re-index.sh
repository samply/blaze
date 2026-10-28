#!/bin/bash
set -euo pipefail

# creates a re-index job for the search param with URL $1, waits until it
# completes and expects $2 resources to be processed

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
search_param_url="$1"

re_index_job() {
cat <<END
{
  "resourceType": "Task",
  "meta": {
    "profile": [
      "https://blaze-server.org/fhir/StructureDefinition/ReIndexJob"
    ]
  },
  "status": "ready",
  "intent": "order",
  "code": {
    "coding": [
      {
        "code": "re-index",
        "system": "https://blaze-server.org/fhir/CodeSystem/JobType",
        "display": "(Re)Index a Search Parameter"
      }
    ]
  },
  "authoredOn": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "input": [
    {
      "type": {
        "coding": [
          {
            "code": "search-param-url",
            "system": "https://blaze-server.org/fhir/CodeSystem/ReIndexJobParameter"
          }
        ]
      },
      "valueCanonical": "$search_param_url"
    }
  ]
}
END
}

job_id=$(re_index_job | create "$base/__admin/Task" | jq -r .id)
echo "created re-index job $job_id"

for _ in {1..60}; do
  job=$(curl -sfH 'Accept: application/fhir+json' "$base/__admin/Task/$job_id")
  status=$(echo "$job" | jq -r .status)
  if [ "$status" = "completed" ] || [ "$status" = "failed" ]; then
    break
  fi
  sleep 1
done

test "re-index job status" "$status" "completed"

output_uri="https://blaze-server.org/fhir/CodeSystem/ReIndexJobOutput"
resources_processed="$(echo "$job" | jq ".output[] | select(.type.coding[] | select(.system == \"$output_uri\" and .code == \"resources-processed\")) | .valueUnsignedInt")"

test "number of processed resources" "$resources_processed" "$2"
