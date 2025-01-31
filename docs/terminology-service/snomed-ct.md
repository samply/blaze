# SNOMED CT <Badge type="info" text="Feature: TERMINOLOGY_SNOMED_CT"/> <Badge type="warning" text="unreleased"/>

Blaze supports the [SNOMED CT](https://www.snomed.org) terminology in the versions provided in the release files. The environment variable `SNOMED_CT_RELEASE_PATH` has to point to a path of an official SNOMED CT release. The release files are read into memory on each start of Blaze. So the release path has to be always available.

## Copyright

This documentation includes content from SNOMED Clinical Terms® (SNOMED CT®) is copyright of the International Health Terminology Standards Development Organisation (IHTSDO) (trading as SNOMED International). Users of Blaze must have the appropriate SNOMED CT Affiliate license - for more information access http://www.snomed.org/snomed-ct/get-snomed-ct or contact SNOMED International via email at info@snomed.org.

## FHIR Documentation

[Using SNOMED CT with FHIR](https://terminology.hl7.org/SNOMEDCT.html)

## Filters

| Property | Operators           | Values         |
|----------|---------------------|----------------|
| concept  | is-a, descendent-of | SNOMED CT code |
| parent   | =                   | SNOMED CT code |
