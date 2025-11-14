#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
url="https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/CodeSystem/wirkstofftyp"
translation_json=$(curl -sH 'Accept: application/fhir+json' "$base/CodeSystem?url=$url&_summary=true" | jq -r '.entry[0].resource._name.extension[] | select(.url == "http://hl7.org/fhir/StructureDefinition/translation").extension[] | select(.url == "content").valueString')
translation_xml=$(curl -sH 'Accept: application/fhir+xml' "$base/CodeSystem?url=$url&_summary=true" | xq -x '/Bundle/entry/resource/CodeSystem/name/extension/extension[@url="content"]/valueString/@value')

test "translation (JSON)" "MII_CS_Medikation_IngredientType" "$translation_json"
test "translation (XML)" "MII_CS_Medikation_IngredientType" "$translation_xml"
