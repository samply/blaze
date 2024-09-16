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
    }
  ]
}
END
}

bundle_evaluate_measure() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "POST",
        "url": "Measure/$1/\$evaluate-measure"
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

evaluate_measure() {
   bundle_evaluate_measure "$2" | jq -cM ".entry[0].resource = $(parameters)" | transact_return_representation "$1"
}

fetch_patients() {
  curl -s "$1/Patient?_list=$2&_count=200"
}

BASE="http://localhost:8080/fhir"
NAME=$1
EXPECTED_COUNT=$2

DATA=$(base64 < "modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/$NAME.cql" | tr -d '\n')
LIBRARY_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')
MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_library "$LIBRARY_URI" "$DATA" | create "$BASE/Library" > /dev/null

MEASURE_ID=$(create_measure "$MEASURE_URI" "$LIBRARY_URI" | create "$BASE/Measure" | jq -r .id)
BUNDLE=$(evaluate_measure "$BASE" "$MEASURE_ID")
COUNT=$(echo "$BUNDLE" | jq -r ".entry[0].resource.group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "✅ count ($COUNT) equals the expected count"
else
  echo "🆘 count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$BUNDLE" | jq .
  exit 1
fi

LIST_ID=$(echo "$BUNDLE" | jq -r '.entry[0].resource.group[0].population[0].subjectResults.reference | split("/")[1]')
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
