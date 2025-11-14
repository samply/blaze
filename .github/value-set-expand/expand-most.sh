#!/bin/bash -e

base="http://localhost:8080/fhir"

start=$(date +%s)

for url in $(blazectl --server "$base" download ValueSet -q "_elements=url&_count=500" 2>/dev/null | jq -r '.url'); do
  if [[ "$url" == "http://dicom.nema.org/"* ]]; then
    continue
  elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(v2-0567|v2-0568|v3-UnitsOfMeasureCaseSensitive)$"; then
    # All UCUM
    continue
  elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(v3-LogicalObservationIdentifierNamesAndCodes|v3-ObservationType|v3-DocumentSectionType|v3-xActBillableCode)$"; then
    # All LOINC
    continue
  #elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(v3-HL7FormatCodes)$"; then
    # unsupported = filter operation
  #  continue
  elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(v3-EPSG-GeodeticParameterDataset|v3-SCDHEC-GISSpatialAccuracyTiers|v2-notAllCodes-0347|v3-PharmacistHIPAA|v3-ObservationCoordinateAxisType|v3-LaboratoryObservationSubtype|v3-KnowledgeSubjectObservationValue|v3-EmergencyMedicalServiceProviderHIPAA|v3-ObservationCoordinateSystemType|v3-DentistHIPAA|v3-AmbulanceHIPAA|v3-HumanLanguage|v3-KnowledgeSubtopicObservationValue|v3-ActProcedureCodeCCI|v3-OrganizationIndustryClassNAICS|v3-USEncounterReferralSource|cpt-all|cpt-modifiers|cpt-base|cpt-usable|v2-2.3.1-0360|v2-2.4-0391|v2-0351|v2-0962|v2-0963|v2-0227|v2-0456|v2-0895|v2-0292|v2-0396|v3-HealthcareServiceLocation|v3-NUCCProviderCodes|v3-ActInjuryCodeCSA|v3-IndustryClassificationSystem|v3-HealthCareCommonProcedureCodingSystem|v3-USEncounterDischargeDisposition|v2-0203|v2-0350|v3-EmploymentStatusUB92|v3-DiagnosisICD9CM|v3-AgeGroupObservationValue|v2-2.6-0391|v2-2.1-0006|v2-2.4-0006|v2-2.7-0360)$"; then
    # unknown code system
    continue
  #elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(v3-VideoMediaType|v3-TextMediaType|v3-TriggerEventID|v3-MultipartMediaType|v3-AudioMediaType|v3-ApplicationMediaType|v3-ImageMediaType|v3-ModelMediaType|v3-MediaType|insuranceplan-type)$"; then
    # content: fragment
  #  continue
  elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(v2-notAllCodes-0399|v3-EntityCode|v3-Country|v3-PlaceEntityType|v3-Country2|v3-CountryEntityType)$"; then
    # content: not-present
    continue
  elif echo "$url" | grep -q -E "^http://terminology\.hl7\.org/ValueSet/(service-category|service-type|appointment-cancellation-reason)$"; then
    # content: example
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(appointment-cancellation-reason|service-type|service-category)$"; then
    # content: example
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(iso3166-1-2|iso3166-1-N|iso3166-1-3|mimetypes|jurisdiction|all-languages|written-language|languages)$"; then
    # content: not-present
    continue
  elif [ "$url" == "http://hl7.org/fhir/ValueSet/observation-codes" ]; then
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(questionnaire-answers|condition-predecessor|sequence-species|condition-cause|dataelement-sdcobjectclass)$"; then
    # Expanding all SNOMED CT concepts is too costly
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(texture-code|supplement-type|consistency-type|entformula-type)$"; then
    # unsupported SCT US Edition
    continue
  #elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(example-filter)$"; then
    # unsupported = filter operation
  #  continue
  elif [ "$url" == "http://hl7.org/fhir/ValueSet/patient-contactrelationship" ]; then
    # unsupported is-not-a filter operation
    continue
  elif [ "$url" == "http://hl7.org/fhir/ValueSet/timezones" ]; then
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(cpt-all|device-safety|provider-taxonomy|currencies|clinvar|consent-content-class|variants|cosmic|specimen-collection-priority|dbsnp|sequence-quality-method|sequence-referenceSeq|ensembl|sequence-quality-standardSequence|vaccine-code|allelename|dataelement-sdcobjectclassproperty|sequenceontology|ref-sequences)$"; then
    # unknown code system
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(use-context)$"; then
    # unknown value set
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(all-distance-units|all-time-units|ucum-units)$"; then
    # All UCUM
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(report-codes|questionnaire-questions|consent-content-code)$"; then
    # All LOINC
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(example-extensional)$"; then
    # LOINC 2.36 not supported
    continue
  elif echo "$url" | grep -q -E "^https://fhir\.kbv\.de/ValueSet/(KBV_VS_Base_Breastfeeding_Status_LOINC|KBV_VS_Base_Glucose_Concentration_LOINC|KBV_VS_Base_Apgar_Score_Identifier_LOINC|KBV_VS_Base_Pregnancy_Status_LOINC|KBV_VS_Base_Estimated_Date_of_Delivery_LOINC)$"; then
    # LOINC 2.77 not supported
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(care-team-category)$"; then
    # LOINC unsupported is-a operator
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/ValueSet/(example-intensional)$"; then
    # LOINC Unsupported = filter property parent
    continue
  elif echo "$url" | grep -q -E "^https://www\.medizininformatik-initiative\.de/fhir/ext/modul-patho/ValueSet/mii-vs-patho-all-loinc$"; then
    # All LOINC
    continue
  elif [[ "$url" == "http://ihe.net/fhir/ihe.formatcode.fhir/ValueSet/formatcode" ]]; then
    # code system urn:ietf:rfc:3986 not found
    continue
  elif [[ "$url" == "http://ihe-d.de/ValueSets/IHEXDSeventCodeList" ]]; then
    # value set http://dvmd.de/fhir/ValueSet/kdl not found
    continue
  elif echo "$url" | grep -q -E  "^http://ihe-d\.de/ValueSets/IHEXDSformatCode(INTL|DE)$"; then
    # code system urn:dicom:uid not found
    continue
  elif echo "$url" | grep -q -E "^http://hl7\.org/fhir/uv/ips/ValueSet/(whoatc-uv-ips|vaccines-whoatc-uv-ips|medication-example-uv-ips)$"; then
    # content: not-present
    continue
  elif [ "$url" == "http://hl7.org/fhir/uv/ips/ValueSet/healthcare-professional-roles-uv-ips" ]; then
    # content: not-present (urn:oid:2.16.840.1.113883.2.9.6.2.7)
    continue
  elif [ "$url" == "http://hl7.org/fhir/uv/genomics-reporting/ValueSet/hgvs-vs" ]; then
    # content: not-present (http://varnomen.hgvs.org)
    continue
  elif [[ "$url" == "urn:oid:"* ]]; then
    continue
  elif [ "$url" == "https://fhir.kbv.de/ValueSet/KBV_VS_Base_Deuev_Anlage_8" ]; then
    # unknown code system (http://fhir.de/CodeSystem/deuev/anlage-8-laenderkennzeichen)
    continue
  elif [ "$url" == "https://fhir.kbv.de/ValueSet/KBV_VS_Base_CommonLanguages" ]; then
    # content: not-present (urn:ietf:bcp:47)
    continue
  elif [[ "$url" == "http://fhir.de/ValueSet/abdata/"* ]]; then
    # content: not-present (http://fhir.de/CodeSystem/abdata/wg14)
    continue
  elif [ "$url" == "http://fhir.de/ValueSet/ifa/pzn" ]; then
    # content: not-present (http://fhir.de/CodeSystem/ifa/pzn)
    continue
  else
    echo "Expand ValueSet: $url"
    curl -skH "Accept: application/fhir+json" -o /dev/null "$base/ValueSet/\$expand?url=$url"
  fi
done

end=$(date +%s)
echo "All Successful in: $((end - start)) seconds"
