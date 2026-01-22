#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"

expand() {
  curl -sfH "Accept: application/fhir+json" "$base/ValueSet/\$expand?url=$1" | jq -r '.expansion.contains[] | [.system, .code, .display] | @csv' | sort -d -t, -k 1,2
}

expand_count() {
  curl -sfH "Accept: application/fhir+json" "$base/ValueSet/\$expand?url=$1" | jq -r '.expansion.contains | length'
}

test_csv() {
  if [ "$2" = "$(cat "$script_dir/$1.csv")" ]; then
    echo "✅ the $1 matches"
  else
    echo "🆘 the $1 is $2, expected $(cat "$script_dir/$1.csv")"
    exit 1
  fi
}

test_csv "medicine-route-of-administration" "$(expand "http://hl7.org/fhir/uv/ips/ValueSet/medicine-route-of-administration")"

test_csv "Abrechnungsart" "$(expand "http://fhir.de/ValueSet/dkgev/Abrechnungsart")"
test_csv "AbrechnungsDiagnoseProzedur" "$(expand "http://fhir.de/ValueSet/AbrechnungsDiagnoseProzedur")"
test_csv "Diagnosesubtyp" "$(expand "http://fhir.de/ValueSet/Diagnosesubtyp")"

test_csv "identifier-type-codes" "$(expand "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/identifier-type-codes")"
test_csv "location-physical-type" "$(expand "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/location-physical-type")"
test_csv "mii-vs-consent-answer" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-answer")"
test_csv "mii-vs-consent-policy" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-policy")"
test_csv "mii-vs-consent-signaturetypes" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-signaturetypes")"

test_csv "ICD-10-GM-E10" "$(expand "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<<E10")"
test_csv "ICD-10-GM-E10-E14" "$(expand "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<<E10-E14")"

test_csv "OPS-3-20" "$(expand "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/ops)concept<<3-20")"
test_csv "OPS-8-19" "$(expand "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/ops)concept<<8-19")"
test "number of Operationen" "$(expand_count "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/ops)concept<<5")" "27826"

test_csv "ATC-A10" "$(expand "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/atc)concept<<A10")"
test_csv "ATC-L03AA02" "$(expand "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/atc)concept<<L03AA02")"

test_csv "SCT-119297000" "$(expand "http://fhir.org/VCL?v1=(http://snomed.info/sct)concept<<119297000")"
