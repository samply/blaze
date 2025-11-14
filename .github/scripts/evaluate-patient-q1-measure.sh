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

create-library() {
  library | jq -cM ".url = \"urn:uuid:$1\" | .content[0].data = \"$2\""
}

create-measure() {
  measure | jq -cM ".url = \"urn:uuid:$1\" | .library[0] = \"urn:uuid:$2\""
}

evaluate-measure() {
  curl -s "$1/Measure/$2/\$evaluate-measure?periodStart=2000&periodEnd=2030&subject=$3"
}

base="http://localhost:8080/fhir"
file="modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/q1.cql"
data=$(base64 "$file" | tr -d '\n')
library_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')
measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create-library "$library_uri" "$data" | create "$base/Library" > /dev/null

measure_id=$(create-measure "$measure_uri" "$library_uri" | create "$base/Measure" | jq -r .id)

male_patient_id=$(curl -s "$base/Patient?gender=male&_count=1" | jq -r '.entry[].resource.id')
count=$(evaluate-measure "$base" "$measure_id" "$male_patient_id" | jq -r ".group[0].population[0].count")
if [ "$count" = "1" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != 1"
  exit 1
fi

female_patient_id=$(curl -s "$base/Patient?gender=female&_count=1" | jq -r ".entry[].resource.id")
count=$(evaluate-measure "$base" "$measure_id" "$female_patient_id" | jq -r ".group[0].population[0].count")
if [ "$count" = "0" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != 0"
  exit 1
fi
