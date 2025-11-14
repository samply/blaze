#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

base="http://localhost:8080/fhir"

error_message="$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Patient\"}" "$base/__admin/Task" | jq -r '.issue[].diagnostics')"
test "error message" "$error_message" "Incorrect resource type \`Patient\`. Expected type is \`Task\`."

error_message="$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Task\"}" "$base/__admin/Task" | jq -r '.issue[].details.text')"
test "error message" "$error_message" "No allowed profile found."

re-index-job() {
cat <<END
{
  "resourceType": "Task",
  "meta": {
    "profile": [
      "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"
    ]
  },
  "status": "on-hold",
  "intent": "order",
  "code": {
    "coding": [
      {
        "code": "re-index",
        "system": "https://samply.github.io/blaze/fhir/CodeSystem/JobType",
        "display": "(Re)Index a Search Parameter"
      }
    ]
  },
  "authoredOn": "2024-04-13T10:05:20.927Z",
  "input": [
    {
      "type": {
        "coding": [
          {
            "code": "search-param-url",
            "system": "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter",
            "display": "Search Param URL"
          }
        ]
      },
      "valueCanonical": "http://hl7.org/fhir/SearchParameter/Resource-profile"
    }
  ]
}
END
}

error_message="$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(re-index-job)" "$base/__admin/Task" | jq -r '.issue[0].diagnostics')"
test "error message" "$error_message" "Constraint failed: status-reason-on-hold: 'Assigns possible reasons to the 'on-hold' status.' (defined in https://samply.github.io/blaze/fhir/StructureDefinition/Job)"
