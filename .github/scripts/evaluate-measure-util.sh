#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

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
  MEASURE_URI="$1"
  NAME="$2"
  DATA="$(base64 < "modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/$NAME.cql" | tr -d '\n')"
  LIBRARY_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')
  LIBRARY="$(create_library "$LIBRARY_URI" "$DATA")"
  MEASURE="$(create_measure "$MEASURE_URI" "$LIBRARY_URI")"
  bundle_library_measure | jq -cM ".entry[0].resource = $LIBRARY | .entry[1].resource = $MEASURE"
}
