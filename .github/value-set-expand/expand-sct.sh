#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"

expand() {
  curl -sfH "Accept: application/fhir+json" "$base/ValueSet/\$expand?url=$1" | jq -r '.expansion.contains[] | [.system, .code, .display] | @csv' | sort
}

expand_filter() {
  curl -sfH "Accept: application/fhir+json" "$base/ValueSet/\$expand?url=$1&filter=$2" | jq -r '.expansion.contains[].code'
}

test_csv() {
  if [ "$2" = "$(cat "$script_dir/$1.csv")" ]; then
    echo "✅ the $1 matches"
  else
    echo "🆘 the $1 is $2, expected $(cat "$script_dir/$1.csv")"
    exit 1
  fi
}

test_csv "BodySite-Observation-Beatmung" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/vs-mii-icu-bodysite-observation-beatmung")"
test_csv "Category-Procedure-Beatmung-SNOMED" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/vs-mii-icu-category-procedure-beatmung-snomed")"
test_csv "Code-Observation-extrakorporale-Verfahren-SNOMED" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/vs-mii-icu-code-observation-extrakorporale-verfahren-snomed")"
test_csv "Code-Monitoring-und-Vitaldaten-SNOMED" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/vs-mii-icu-code-monitoring-und-vitaldaten-snomed")"

test "BodySite-Observation-Beatmung" "$(expand_filter "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/vs-mii-icu-bodysite-observation-beatmung" "lung")" "181216001"
