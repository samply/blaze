#!/usr/bin/env bash

# Usage: ./evaluate-measure.sh -f <query>.cql <server-base>

# Takes a CQL file, creates a Library resource from it, references that from a
# Measure resource and calls $evaluate-measure on it.

library() {
cat <<END
{
  "resourceType": "Library",
  "status": "active",
  "type" : {
    "coding" : [
      {
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
  "subjectCodeableConcept": {
    "coding": [
      {
        "system": "http://hl7.org/fhir/resource-types",
        "code": "Patient"
      }
    ]
  },
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
            "language": "text/cql",
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
  measure | jq -cM ".url = \"urn:uuid:$1\" | .library[0] = \"urn:uuid:$2\" | .subjectCodeableConcept.coding[0].code = \"$3\""
}

post() {
  curl -sH "Content-Type: application/fhir+json" -d @- "${BASE}/$1"
}

evaluate-measure() {
  time curl -s "${BASE}/Measure/$1/\$evaluate-measure?periodStart=2000&periodEnd=2019"
}

usage()
{
  echo "Usage: $0 -f QUERY_FILE [ -t type ] BASE"
  exit 2
}

unset FILE TYPE BASE

while getopts 'f:t:' c
do
  case ${c} in
    f) FILE=$OPTARG ;;
    t) TYPE=$OPTARG ;;
  esac
done

shift $((OPTIND-1))
BASE=$1

[[ -z "$FILE" ]] && usage
[[ -z "$TYPE" ]] && TYPE="Patient"
[[ -z "$BASE" ]] && usage

DATA=$(cat ${FILE} | base64 | tr -d '\n')
LIBRARY_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')
MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create-library ${LIBRARY_URI} ${DATA} | post "Library" > /dev/null

MEASURE_ID=$(create-measure ${MEASURE_URI} ${LIBRARY_URI} ${TYPE} | post "Measure" | jq -r .id)

COUNT=$(evaluate-measure ${MEASURE_ID} | jq ".group[0].population[0].count")

echo "Count: ${COUNT}"
