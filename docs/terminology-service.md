# Terminology Service <Badge type="info" text="Feature: TERMINOLOGY_SERVICE"/> <Badge type="warning" text="Since 0.32"/>

> [!NOTE]
> The terminology service is an optional feature that has to be enabled by setting `ENABLE_TERMINOLOGY_SERVICE` to `true`.

## Supported Code Systems

### FHIR Code Systems

[FHIR CodeSystem](terminology-service/fhir.md) resources stored in Blaze with `content` of either `complete` or `fragment` are supported. 

### External Code Systems

Currently the external code systems shown in the following table are supported.

| Name                                                    | Version(s)            | Notes                             |
|---------------------------------------------------------|-----------------------|-----------------------------------|
| [BCP-13](terminology-service/bcp-13.md) (Media Types)   | 1.0.0                 | enabled by default                |
| [BCP-47](terminology-service/bcp-47.md) (Language Tags) | 1.0.0                 | enabled by default                |
| [LOINC](terminology-service/loinc.md)                   | 2.78                  | has to be enabled                 |
| [SNOMED CT](terminology-service/snomed-ct.md)           | all versions provided | release files have to be provided |
| [UCUM](terminology-service/ucum.md)                     | 2013.10.21            | enabled by default                |

## Operations

* [CodeSystem $validate-code](api/operation/code-system-validate-code.md)
* [ValueSet $expand](api/operation/value-set-expand.md)
* [ValueSet $validate-code](api/operation/value-set-validate-code.md)

## Graph Cache

For stored FHIR code systems, a graph will be build before operations like $validate-code are executed. Building those graph is quite expensive. I order to prevent Blaze to build a graph each time an operation is executed, a graph cache is used. The environment variable `TERMINOLOGY_SERVICE_GRAPH_CACHE_SIZE` allows to set the number of concepts, the graph cache should hold. The default is 100,000.

## Memory Requirements

Because both the LOINC and SNOMED CT data used for terminology operations is currently hold completely in memory, at least 8 GiB of Java Heap memory is required. The Java Heap memory can be set by setting `JAVA_TOOL_OPTIONS` to `-Xmx8g`.

## Validation

The terminology service is designed to work together with the [FHIR Validator][1]. More on that in the [Terminology Service – Validation](terminology-service/validation.md) section.

## Performance

Performance data can be found in the [Performance – Terminology Service](performance/terminology-service.md) section.

## Example Deployment

The full deployment documentation is available [here](deployment/full-standalone.md). In this section the configuration of a standalone backend only terminology service deployment configuration is shown. That configuration can be adapted into the full deployment scenario with frontend.

The SNOMED CT release file has to be uncompressed into a Docker mountable directory. In this example a local directory called `sct-release`. The Docker Compose file would look like this:

```yaml
services:
  blaze:
    image: "samply/blaze:latest"
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx8g"
      DB_BLOCK_CACHE_SIZE: "2048"
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

[1]: <https://confluence.hl7.org/spaces/FHIR/pages/35718580/Using+the+FHIR+Validator>
