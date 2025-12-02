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

base="http://localhost:8080/fhir"
name=$1
expected_count=$2

data=$(base64 < "modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/$name.cql" | tr -d '\n')
library_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')
measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_library "$library_uri" "$data" | create "$base/Library" > /dev/null

measure_id=$(create_measure "$measure_uri" "$library_uri" | create "$base/Measure" | jq -r .id)
bundle=$(evaluate_measure "$base" "$measure_id")
count=$(echo "$bundle" | jq -r ".entry[0].resource.group[0].population[0].count")

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != $expected_count"
  echo "Report:"
  echo "$bundle" | jq .
  exit 1
fi

list_id=$(echo "$bundle" | jq -r '.entry[0].resource.group[0].population[0].subjectResults.reference | split("/")[1]')
patient_bundle=$(fetch_patients "$base" "$list_id")
id_count=$(echo "$patient_bundle" | jq -r ".entry[].resource.id" | sort -u | wc -l | xargs)

if [ "$id_count" = "$expected_count" ]; then
  echo "✅ downloaded patient count ($id_count) equals the expected count"
else
  echo "🆘 downloaded patient count ($id_count) != $expected_count"
  echo "Patient bundle:"
  echo "$patient_bundle" | jq .
  exit 1
fi
