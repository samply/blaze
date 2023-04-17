# CQL Performance

## Simple Code Search

In this section, CQL Queries for selecting Patients which have Observation resources with a certain code are used.

```text
library "observation-17861-6"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  exists [Observation: Code '17861-6' from loinc]
```

The `DB_RESOURCE_HANDLE_CACHE_SIZE` was set to zero because CQL doesn't benefit from it. The GC settings were: `-XX:+UseG1GC -XX:MaxGCPauseMillis=50`.

The CQL query is executed with the following `blazectl` command:

```sh
blazectl evaluate-measure "cql/observation-$CODE.yml" --server http://localhost:8080/fhir | jq -rf cql/result.jq
```

| CPU        | Heap Mem | Block Cache | # Pat. ¹ | # Obs. ² | Code    | # Hits | Time (s) | StdDev | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|-------:|---------:|-------:|---------:|
| EPYC 7543P |     1 GB |        4 GB |    100 k |     28 M | 17861-6 |   15 k |     0.23 |  0.037 |      2.3 |
| EPYC 7543P |     1 GB |        4 GB |    100 k |     28 M | 39156-5 |   99 k |     0.25 |  0.034 |      2.5 |
| EPYC 7543P |     1 GB |        4 GB |    100 k |     28 M | 29463-7 |  100 k |     0.25 |  0.035 |      2.5 |
| EPYC 7543P |     1 GB |       32 GB |      1 M |    278 M | 17861-6 |  149 k |     2.37 |  0.096 |      2.4 |
| EPYC 7543P |     1 GB |       32 GB |      1 M |    278 M | 39156-5 |  995 k |     2.64 |  0.103 |      2.6 |
| EPYC 7543P |     1 GB |       32 GB |      1 M |    278 M | 29463-7 |    1 M |     2.65 |  0.082 |      2.7 |

¹ Number of Patients, ² Number of Observations, ³ Time in seconds per 1 million patients, The amount of system memory was 128 GB in all cases.

## Code and Value Search

In this section, CQL Queries for selecting Patients which have Observation resources with a certain code and value are used.

```text
library "observation-body-weight-50"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
code "body-weight": '29463-7' from loinc

context Patient

define InInitialPopulation:
  exists [Observation: "body-weight"] O where O.value < 73.3 'kg'
```

The `DB_RESOURCE_HANDLE_CACHE_SIZE` was set to zero because CQL doesn't benefit from it. The GC settings were: `-XX:+UseG1GC -XX:MaxGCPauseMillis=50`.

The CQL query is executed with the following `blazectl` command:

```sh
blazectl evaluate-measure "cql/observation-$CODE-$VALUE.yml" --server http://localhost:8080/fhir | jq -rf cql/result.jq
```

| CPU        | Heap Mem | Block Cache | # Pat. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) | StdDev |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|-------:|
| EPYC 7543P |     2 GB |        4 GB |    100 k |     28 M | 29463-7 |  11.1 |   10 k |     1.02 |  0.023 |
| EPYC 7543P |     2 GB |        4 GB |    100 k |     28 M | 29463-7 |  73.3 |   50 k |     0.70 |  0.026 |
| EPYC 7543P |     2 GB |        4 GB |    100 k |     28 M | 29463-7 |   185 |  100 k |     0.28 |  0.025 |
| EPYC 7543P |    20 GB |       32 GB |      1 M |    278 M | 17861-6 |  11.1 |  100 k |     ---- |  ----- |
| EPYC 7543P |    20 GB |       32 GB |      1 M |    278 M | 39156-5 |  73.3 |  500 k |     3.20 |  0.179 |
| EPYC 7543P |    20 GB |       32 GB |      1 M |    278 M | 29463-7 |   185 |    1 M |     5.92 |  0.082 |

¹ Number of Patients, ² Number of Observations, The amount of system memory was 128 GB in all cases.

## Observation Code Search

In this section, CQL Queries for selecting Patients which have Observation resources with a certain code and value are used.

```text
library "observation-category-laboratory"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem "observation-category": 'http://terminology.hl7.org/CodeSystem/observation-category'
code "laboratory": 'laboratory' from "observation-category"

context Patient

define InInitialPopulation:
  [Observation: category in "laboratory"]
```

The `DB_RESOURCE_HANDLE_CACHE_SIZE` was set to zero because CQL doesn't benefit from it. The GC settings were: `-XX:+UseG1GC -XX:MaxGCPauseMillis=50`.

The CQL query is executed with the following `blazectl` command:

```sh
blazectl evaluate-measure "cql/observation-category-$CODE.yml" --server http://localhost:8080/fhir | jq -rf cql/result.jq
```

| CPU        | Heap Mem | Block Cache | # Pat. ¹ | # Obs. ² | Code       | # Hits | Time (s) | StdDev |
|------------|---------:|------------:|---------:|---------:|------------|-------:|---------:|-------:|
| EPYC 7543P |     1 GB |        4 GB |    100 k |     28 M | laboratory |   17 M |     10.1 |  0.315 |
| EPYC 7543P |     1 GB |       40 GB |      1 M |    278 M | laboratory |  170 M |     92.8 |  2.057 |

¹ Number of Patients, ² Number of Observations, The amount of system memory was 128 GB in all cases.
