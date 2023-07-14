#!/usr/bin/env bash

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

create-library() {
  library | jq -cM ".url = \"urn:uuid:$1\" | .content[0].data = \"$2\""
}

create-measure() {
  measure | jq -cM ".url = \"urn:uuid:$1\" | .library[0] = \"urn:uuid:$2\""
}

post() {
  curl -sH "Content-Type: application/fhir+json" -d @- "$1/$2"
}

evaluate-measure() {
  curl -s "$1/Measure/$2/\$evaluate-measure?periodStart=2000&periodEnd=2030&subject=$3"
}

BASE="http://localhost:8080/fhir"
FILE="modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/q1.cql"
DATA=$(base64 "$FILE" | tr -d '\n')
LIBRARY_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')
MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create-library "$LIBRARY_URI" "$DATA" | post "$BASE" "Library" > /dev/null

MEASURE_ID=$(create-measure "$MEASURE_URI" "$LIBRARY_URI" | post "$BASE" "Measure" | jq -r .id)

MALE_PATIENT_ID=$(curl -s "$BASE/Patient?gender=male&_count=1" | jq -r '.entry[].resource.id')
COUNT=$(evaluate-measure "$BASE" "$MEASURE_ID" "$MALE_PATIENT_ID" | jq -r ".group[0].population[0].count")
if [ "$COUNT" = "1" ]; then
  echo "OK 👍: count ($COUNT) equals the expected count"
else
  echo "Fail 😞: count ($COUNT) != 1"
  exit 1
fi

FEMALE_PATIENT_ID=$(curl -s "$BASE/Patient?gender=female&_count=1" | jq -r ".entry[].resource.id")
COUNT=$(evaluate-measure "$BASE" "$MEASURE_ID" "$FEMALE_PATIENT_ID" | jq -r ".group[0].population[0].count")
if [ "$COUNT" = "0" ]; then
  echo "OK 👍: count ($COUNT) equals the expected count"
else
  echo "Fail 😞: count ($COUNT) != 0"
  exit 1
fi
