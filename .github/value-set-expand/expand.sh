#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../scripts/util.sh"

BASE="http://localhost:8080/fhir"

expand() {
  curl -sfH "Accept: application/fhir+json" "$BASE/ValueSet/\$expand?url=$1" | jq -r '.expansion.contains[] | [.system, .code] | @csv' | sort
}

test() {
  if [ "$2" = "$(cat "$SCRIPT_DIR/$1.csv")" ]; then
    echo "✅ the $1 matches"
  else
    echo "🆘 the $1 is $2, expected $(cat "$SCRIPT_DIR/$1.csv")"
    exit 1
  fi
}

test "Abrechnungsart" "$(expand "http://fhir.de/ValueSet/dkgev/Abrechnungsart")"
test "AbrechnungsDiagnoseProzedur" "$(expand "http://fhir.de/ValueSet/AbrechnungsDiagnoseProzedur")"
test "Diagnosesubtyp" "$(expand "http://fhir.de/ValueSet/Diagnosesubtyp")"

test "identifier-type-codes" "$(expand "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/identifier-type-codes")"
test "location-physical-type" "$(expand "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/location-physical-type")"
test "mii-vs-consent-answer" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-answer")"
test "mii-vs-consent-policy" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-policy")"
test "mii-vs-consent-signaturetypes" "$(expand "https://www.medizininformatik-initiative.de/fhir/modul-consent/ValueSet/mii-vs-consent-signaturetypes")"

for url in $(blazectl --server "$BASE" download ValueSet -q "_elements=url&_count=500" 2>/dev/null | jq -r '.url'); do
  if [[ "$url" == "http://dicom.nema.org/"* ]]; then
    continue
  elif [[ "$url" == "http://terminology.hl7.org/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "http://hl7.org/fhir/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "http://loinc.org/"* ]]; then
    continue
  elif [[ "$url" == "http://ihe.net/fhir/"* ]]; then
    continue
  elif [[ "$url" == "http://ihe-d.de/ValueSets/"* ]]; then
    continue
  elif [[ "$url" == "http://hl7.org/fhir/uv/ips/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "http://hl7.org/fhir/uv/genomics-reporting/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "http://cts.nlm.nih.gov/fhir/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "urn:oid:"* ]]; then
    continue
  elif [[ "$url" == "https://fhir.kbv.de/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "http://fhir.de/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "http://fhir.de/ConsentManagement/ValueSet/TemplateType" ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/modul-mikrobio/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/modul-mikrobiologie/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/ext/modul-patho/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/ValueSet/"* ]]; then
    continue
  elif [[ "$url" == "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/ValueSet/"* ]]; then
    continue
  else
    echo "Expand ValueSet with URL: $url"
    curl -sfH "Accept: application/fhir+json" -o /dev/null "$BASE/ValueSet/\$expand?url=$url"
  fi
done
