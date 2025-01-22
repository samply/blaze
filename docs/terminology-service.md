# Terminology Service

> [!CAUTION]
> The terminology service is currently **beta**. Only the basic functionality, described here, is implemented.

## Operations

* [CodeSystem $validate-code](api/operation-code-system-validate-code.md)
* [ValueSet $expand](api/operation-value-set-expand.md)
* [ValueSet $validate-code](api/operation-value-set-validate-code.md)

## Enable LOINC

LOINC data is build into the Blaze image. Because LOINC support needs additional memory, it has to be enabled by setting the environment variable `ENABLE_TERMINOLOGY_LOINC` to `true`.

## Enable Snomed CT

Because Snomed CT has to be licensed, Blaze doesn't contain the Snomed CT code system by default. However, by setting the environment variable `ENABLE_TERMINOLOGY_SNOMED_CT` to `true` and `SNOMED_CT_RELEASE_PATH` to a path of an official Snomed CT release, Blaze will be able to offer terminology services on Snomed CT. The release files are read into memory on each start of Blaze. So the release path has to be always available.
