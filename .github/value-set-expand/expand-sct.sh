#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../scripts/util.sh"

BASE="http://localhost:8080/fhir"

expand() {
  curl -sfH "Accept: application/fhir+json" "$BASE/ValueSet/\$expand?url=$1" | jq -r '.expansion.contains[] | [.system, .code, .display] | @csv' | sort
}

test() {
  if [ "$2" = "$(cat "$SCRIPT_DIR/$1.csv")" ]; then
    echo "✅ the $1 matches"
  else
    echo "🆘 the $1 is $2, expected $(cat "$SCRIPT_DIR/$1.csv")"
    exit 1
  fi
}

test "BodySite-Observation-Beatmung" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/BodySite-Observation-Beatmung")"
test "Category-Procedure-Beatmung-SNOMED" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/Category-Procedure-Beatmung-SNOMED")"
test "Code-Observation-extrakorporale-Verfahren-SNOMED" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/Code-Observation-extrakorporale-Verfahren-SNOMED")"
test "Code-Monitoring-und-Vitaldaten-SNOMED" "$(expand "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/Code-Monitoring-und-Vitaldaten-SNOMED")"
