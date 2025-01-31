# Terminology Service <Badge type="info" text="Feature: TERMINOLOGY_SERVICE"/> <Badge type="warning" text="unreleased"/>

> [!CAUTION]
> The terminology service is currently **beta**. Only the basic functionality, described here, is implemented.

The terminology service is an optional feature that has to be enabled by setting `ENABLE_TERMINOLOGY_SERVICE` to `true`.

## Operations

* [CodeSystem $validate-code](api/operation/code-system-validate-code.md)
* [ValueSet $expand](api/operation/value-set-expand.md)
* [ValueSet $validate-code](api/operation/value-set-validate-code.md)

## Enable LOINC

LOINC data is build into the Blaze image. Because LOINC support needs additional memory, it has to be enabled by setting the environment variable `ENABLE_TERMINOLOGY_LOINC` to `true`.

## Enable SNOMED CT

Because SNOMED CT has to be licensed, Blaze doesn't contain the SNOMED CT code system by default. However, by setting the environment variable `ENABLE_TERMINOLOGY_SNOMED_CT` to `true` and `SNOMED_CT_RELEASE_PATH` to a path of an official SNOMED CT release, Blaze will be able to offer terminology services on SNOMED CT. The release files are read into memory on each start of Blaze. So the release path has to be always available.

## Memory Requirements

Because both the LOINC and SNOMED CT data used for terminology operations is currently hold completely in memory, at least 8 GiB of Java Heap memory is required. The Java Heap memory can be set by setting `JAVA_TOOL_OPTIONS` to `-Xmx8g`.

## Example Deployment

The full deployment documentation is available [here](deployment/full-standalone.md). In this section the configuration of a standalone backend only terminology service deployment configuration is shown. That configuration can be adapted into the full deployment scenario with frontend.

The SNOMED CT release file has to be uncompressed into a Docker mountable directory. In this example a local directory called `sct-release`. The Docker Compose file would look like this:

```yaml
services:
  blaze:
    image: "samply/blaze:latest"
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx8g"
      ENABLE_TERMINOLOGY_SERVICE: true
      ENABLE_TERMINOLOGY_LOINC: true
      ENABLE_TERMINOLOGY_SNOMED_CT: true
      SNOMED_CT_RELEASE_PATH: "/app/sct-release"
    ports:
    - "8080:8080"
    volumes:
    - "blaze-data:/app/data"
    - "./sct-release:/app/sct-release"
volumes:
  blaze-data:
```
