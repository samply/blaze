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
  curl -sH "Content-Type: application/fhir+json" -d @- "$base/$1"
}

evaluate-measure() {
    curl -s "$base/Measure/$1/\$evaluate-measure?periodStart=2000&periodEnd=2030"
}

evaluate-measure-list() {
  curl -sd '{"resourceType": "Parameters", "parameter": [{"name": "periodStart", "valueDate": "2000"}, {"name": "periodEnd", "valueDate": "2030"}, {"name": "reportType", "valueCode": "subject-list"}]}' \
    -H "Content-Type: application/fhir+json" "$base/Measure/$1/\$evaluate-measure"
}

usage()
{
  echo "Usage: $0 -f QUERY_FILE [ -t subject-type ] [ -r report-type ] base"
  echo ""
  echo "Example subject-types: Patient, Specimen; default is Patient"
  echo "Possible report-types: subject-list, population; default is population"
  exit 2
}

unset file subject_type report_type base

while getopts 'f:t:r:' c
do
  case ${c} in
    f) file=$OPTARG ;;
    t) subject_type=$OPTARG ;;
    r) report_type=$OPTARG ;;
  esac
done

shift $((OPTIND-1))
base=$1

[[ -z "$file" ]] && usage
[[ -z "$subject_type" ]] && subject_type="Patient"
[[ -z "$base" ]] && usage

subject_type_lower=$(echo $subject_type | tr '[:upper:]' '[:lower:]')
data=$(base64 < "$file" | tr -d '\n')
library_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')
measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create-library ${library_uri} ${data} | post "Library" > /dev/null

measure_id=$(create-measure ${measure_uri} ${library_uri} ${subject_type} | post "Measure" | jq -r .id | tr -d '\r')

if [ "subject-list" = "$report_type" ]; then
  echo "Generating a report including the list of matching ${subject_type_lower}s..."
  measure_report=$(evaluate-measure-list ${measure_id})
  count=$(echo $measure_report | jq -r '.group[0].population[0].count' | tr -d '\r')
  list_reference=$(echo $measure_report | jq -r '.group[0].population[0].subjectResults.reference' | tr -d '\r')
  list_id=$(echo $list_reference | cut -d '/' -f2)

  echo "Found $count ${subject_type_lower}s that can be found on List $base/$list_reference."
  echo ""
  echo "Please use the following blazectl command to download the ${subject_type_lower}s:"
  echo "  blazectl download --server $base $subject_type -q '_list=$list_id' -o ${subject_type_lower}s.ndjson"
else
  echo "Generating a population count report..."
  measure_report=$(evaluate-measure ${measure_id})
  count=$(echo $measure_report | jq -r '.group[0].population[0].count' | tr -d '\r')
  echo "Found $count ${subject_type_lower}s."
fi
