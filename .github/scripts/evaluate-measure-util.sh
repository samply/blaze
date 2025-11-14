#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

library() {
cat <<END
{
  "resourceType": "Library",
  "status": "active",
  "type" : {
    "coding" : [
      {
        "system": "http://terminology.hl7.org/CodeSystem/library-type",
        "code" : "logic-library"
      }
    ]
  },
  "content": [
    {
      "contentType": "text/cql"
    }
  ]
}
END
}

measure() {
cat <<END
{
  "resourceType": "Measure",
  "status": "active",
  "scoring": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/measure-scoring",
        "code": "cohort"
      }
    ]
  },
  "group": [
    {
      "population": [
        {
          "code": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/measure-population",
                "code": "initial-population"
              }
            ]
          },
          "criteria": {
            "language": "text/cql-identifier",
            "expression": "InInitialPopulation"
          }
        }
      ]
    }
  ]
}
END
}

bundle_library_measure() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "POST",
        "url": "Library"
      }
    },
    {
      "request": {
        "method": "POST",
        "url": "Measure"
      }
    }
  ]
}
END
}

create_library() {
  library | jq -cM ".url = \"urn:uuid:$1\" | .content[0].data = \"$2\""
}

create_measure() {
  measure | jq -cM ".url = \"urn:uuid:$1\" | .library[0] = \"urn:uuid:$2\""
}

create_bundle_library_measure() {
  local measure_uri="$1"
  local name="$2"
  local data="$(base64 < "modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/$name.cql" | tr -d '\n')"
  local library_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')
  local library="$(create_library "$library_uri" "$data")"
  local measure="$(create_measure "$measure_uri" "$library_uri")"
  bundle_library_measure | jq -cM ".entry[0].resource = $library | .entry[1].resource = $measure"
}
