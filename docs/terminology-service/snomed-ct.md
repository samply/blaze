# SNOMED CT <Badge type="info" text="Feature: TERMINOLOGY_SNOMED_CT"/> <Badge type="warning" text="Since 0.32"/>

> [!NOTE]
> Because SNOMED CT has to be licensed, Blaze doesn't contain the SNOMED CT code system by default. However, by setting the environment variable `ENABLE_TERMINOLOGY_SNOMED_CT` to `true` and `SNOMED_CT_RELEASE_PATH` to a path of an official SNOMED CT release, Blaze will be able to offer terminology services on SNOMED CT. The release files are read into memory on each start of Blaze. So the release path has to be always available.

Blaze supports the [SNOMED CT](https://www.snomed.org) terminology in the modules and versions provided in the release files. Only one set of release files (edition) can be loaded. In case the modules contain descriptions from different languages, all that languages are supported.

## Copyright

This documentation includes content from SNOMED Clinical Terms® (SNOMED CT®) is copyright of the International Health Terminology Standards Development Organisation (IHTSDO) (trading as SNOMED International). Users of Blaze must have the appropriate SNOMED CT Affiliate license - for more information access http://www.snomed.org/snomed-ct/get-snomed-ct or contact SNOMED International via email at info@snomed.org.

## Filters

| Property | Operators           | Values         |
|----------|---------------------|----------------|
| concept  | is-a, descendent-of | SNOMED CT code |
| parent   | =                   | SNOMED CT code |
| child    | =                   | SNOMED CT code |

## Display

The display contains the preferred term of the language requested with `en` being the default language.

## Designations

Designations contain the fully specified name and all synonyms in all languages. 

## Resources

[Using SNOMED CT with FHIR](https://terminology.hl7.org/SNOMEDCT.html)
