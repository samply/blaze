#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"
. "$script_dir/evaluate-measure-util.sh"

bundle_evaluate_measure() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "Measure/\$evaluate-measure?measure=urn:uuid:$1&periodStart=2000&periodEnd=2030"
      }
    }
  ]
}
END
}

base="http://localhost:8080/fhir"
name="$1"
expected_count="$2"

measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$measure_uri" "$name" | transact "$base" > /dev/null

bundle=$(bundle_evaluate_measure "$measure_uri" | transact "$base")
count=$(echo "$bundle" | jq -r ".entry[0].resource.group[0].population[0].count")

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != $expected_count"
  echo "Report:"
  echo "$bundle" | jq .
  exit 1
fi
