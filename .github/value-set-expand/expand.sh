#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"

expand() {
  curl -sfH "Accept: application/fhir+json" "$base/ValueSet/\$expand?url=$1" | jq -r '.expansion.contains[] | [.system, .code, .display] | @csv' | sort
}

test() {
  if [ "$2" = "$(cat "$script_dir/$1.csv")" ]; then
    echo "✅ the $1 matches"
  else
    echo "🆘 the $1 is $2, expected $(cat "$script_dir/$1.csv")"
    exit 1
  fi
}

test "medicine-route-of-administration" "$(expand "http://hl7.org/fhir/uv/ips/ValueSet/medicine-route-of-administration")"

test "Abrechnungsart" "$(expand "http://fhir.de/ValueSet/dkgev/Abrechnungsart")"
test "AbrechnungsDiagnoseProzedur" "$(expand "http://fhir.de/ValueSet/AbrechnungsDiagnoseProzedur")"
test "Diagnosesubtyp" "$(expand "http://fhir.de/ValueSet/Diagnosesubtyp")"

test "identifier-type-codes" "$(expand "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/identifier-type-codes")"
test "location-physical-type" "$(expand "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/location-physical-type")"
test "mii-vs-consent-answer" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-answer")"
test "mii-vs-consent-policy" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-policy")"
test "mii-vs-consent-signaturetypes" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-signaturetypes")"
