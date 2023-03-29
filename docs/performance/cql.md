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

The CQL query is executed with the following `blazectl` command:

```sh
blazectl "evaluate-measure docs/performance/cql/observation-$CODE.yml" --server http://localhost:8080/fhir | jq -rf docs/performance/cql/result.jq
```

| CPU        | Heap Mem | Block Cache | # Pat. ¹ | # Obs. ² | Code    | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        1 GB |    100 k |     28 M | 17861-6 |   15 k |     0.42 |     4.19 |
| EPYC 7543P |     8 GB |        1 GB |    100 k |     28 M | 39156-5 |   99 k |     0.55 |     5.51 |
| EPYC 7543P |     8 GB |        1 GB |    100 k |     28 M | 29463-7 |  100 k |     0.55 |     5.54 |
| EPYC 7543P |    30 GB |       10 GB |      1 M |    278 M | 17861-6 |  149 k |     4.14 |     4.13 |
| EPYC 7543P |    30 GB |       10 GB |      1 M |    278 M | 39156-5 |  995 k |     5.72 |     5.71 |
| EPYC 7543P |    30 GB |       10 GB |      1 M |    278 M | 29463-7 |    1 M |     5.71 |     5.70 |

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

The CQL query is executed with the following `blazectl` command:

```sh
blazectl evaluate-measure "docs/performance/cql/observation-$CODE-$VALUE.yml" --server http://localhost:8080/fhir | jq -rf docs/performance/cql/result.jq
```

| CPU        | Heap Mem | Block Cache | # Pat. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|
| EPYC 7543P |     8 GB |        1 GB |    100 k |     28 M | 29463-7 |  11.1 |   10 k |     1.97 |
| EPYC 7543P |     8 GB |        1 GB |    100 k |     28 M | 29463-7 |  73.3 |   50 k |     1.20 |
| EPYC 7543P |     8 GB |        1 GB |    100 k |     28 M | 29463-7 |   185 |  100 k |     0.63 |
| EPYC 7543P |    30 GB |       10 GB |      1 M |    278 M | 17861-6 |  11.1 |  100 k |    20.10 |
| EPYC 7543P |    30 GB |       10 GB |      1 M |    278 M | 39156-5 |  73.3 |  500 k |    13.10 |
| EPYC 7543P |    30 GB |       10 GB |      1 M |    278 M | 29463-7 |   185 |    1 M |     5.92 |

¹ Number of Patients, ² Number of Observations, The amount of system memory was 128 GB in all cases.
