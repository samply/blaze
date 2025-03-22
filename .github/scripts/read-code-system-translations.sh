#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
URL="https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/CodeSystem/wirkstofftyp"
TRANSLATION_JSON=$(curl -sH 'Accept: application/fhir+json' "$BASE/CodeSystem?url=$URL&_summary=true" | jq -r '.entry[0].resource._name.extension[] | select(.url == "http://hl7.org/fhir/StructureDefinition/translation").extension[] | select(.url == "content").valueString')
TRANSLATION_XML=$(curl -sH 'Accept: application/fhir+xml' "$BASE/CodeSystem?url=$URL&_summary=true" | xq -x '/Bundle/entry/resource/CodeSystem/name/extension/extension[@url="content"]/valueString/@value')

test "translation (JSON)" "MII_CS_Medikation_IngredientType" "$TRANSLATION_JSON"
test "translation (XML)" "MII_CS_Medikation_IngredientType" "$TRANSLATION_XML"
