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
  measure | jq -cM ".url = \"urn:uuid:$1\" | .library[0] = \"urn:uuid:$2\" | .subjectCodeableConcept.coding[0].code = \"$3\""
}

post() {
  curl -sH "Content-Type: application/fhir+json" -d @- "$BASE/$1"
}

evaluate-measure() {
    curl -s "$BASE/Measure/$1/\$evaluate-measure?periodStart=2000&periodEnd=2030"
}

evaluate-measure-list() {
  curl -sd '{"resourceType": "Parameters", "parameter": [{"name": "periodStart", "valueDate": "2000"}, {"name": "periodEnd", "valueDate": "2030"}, {"name": "reportType", "valueCode": "subject-list"}]}' \
    -H "Content-Type: application/fhir+json" "$BASE/Measure/$1/\$evaluate-measure"
}

usage()
{
  echo "Usage: $0 -f QUERY_FILE [ -t subject-type ] [ -r report-type ] BASE"
  echo ""
  echo "Example subject-types: Patient, Specimen; default is Patient"
  echo "Possible report-types: subject-list, population; default is population"
  exit 2
}

unset FILE SUBJECT_TYPE REPORT_TYPE BASE

while getopts 'f:t:r:' c
do
  case ${c} in
    f) FILE=$OPTARG ;;
    t) SUBJECT_TYPE=$OPTARG ;;
    r) REPORT_TYPE=$OPTARG ;;
  esac
done

shift $((OPTIND-1))
BASE=$1

[[ -z "$FILE" ]] && usage
[[ -z "$SUBJECT_TYPE" ]] && SUBJECT_TYPE="Patient"
[[ -z "$BASE" ]] && usage

SUBJECT_TYPE_LOWER=$(echo $SUBJECT_TYPE | tr '[:upper:]' '[:lower:]')
DATA=$(base64 < "$FILE" | tr -d '\n')
LIBRARY_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')
MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create-library ${LIBRARY_URI} ${DATA} | post "Library" > /dev/null

MEASURE_ID=$(create-measure ${MEASURE_URI} ${LIBRARY_URI} ${SUBJECT_TYPE} | post "Measure" | jq -r .id | tr -d '\r')

if [ "subject-list" = "$REPORT_TYPE" ]; then
  echo "Generating a report including the list of matching ${SUBJECT_TYPE_LOWER}s..."
  MEASURE_REPORT=$(evaluate-measure-list ${MEASURE_ID})
  COUNT=$(echo $MEASURE_REPORT | jq -r '.group[0].population[0].count' | tr -d '\r')
  LIST_REFERENCE=$(echo $MEASURE_REPORT | jq -r '.group[0].population[0].subjectResults.reference' | tr -d '\r')
  LIST_ID=$(echo $LIST_REFERENCE | cut -d '/' -f2)

  echo "Found $COUNT ${SUBJECT_TYPE_LOWER}s that can be found on List $BASE/$LIST_REFERENCE."
  echo ""
  echo "Please use the following blazectl command to download the ${SUBJECT_TYPE_LOWER}s:"
  echo "  blazectl download --server $BASE $SUBJECT_TYPE -q '_list=$LIST_ID' -o ${SUBJECT_TYPE_LOWER}s.ndjson"
else
  echo "Generating a population count report..."
  MEASURE_REPORT=$(evaluate-measure ${MEASURE_ID})
  COUNT=$(echo $MEASURE_REPORT | jq -r '.group[0].population[0].count' | tr -d '\r')
  echo "Found $COUNT ${SUBJECT_TYPE_LOWER}s."
fi
