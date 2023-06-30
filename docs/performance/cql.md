# CQL Performance

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache ¹ |
|--------|----------|------------|------:|--------:|-------:|---------:|------------:|-----------------:|
| LE1080 | on-prem  | EPYC 7543P |    16 | 128 GiB |   2 TB |   16 GiB |      32 GiB |              5 M | 

¹ Size of the resource cache (DB_RESOURCE_CACHE_SIZE)

## Datasets

The following datasets were used:

| Dataset | # Pat. ¹ | # Res. ² | # Obs. ³ |
|---------|---------:|---------:|---------:|
| 100k    |    100 k |    104 M |     59 M |
| 1M      |      1 M |   1044 M |    593 M |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

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
blazectl evaluate-measure "cql/observation-$CODE.yml" --server http://localhost:8080/fhir | jq -rf cql/result.jq
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LE1080 | 100k    | 17861-6 |    2 k |     0.26 |  0.158 | 384.5 k | 
| LE1080 | 100k    | 8310-5  |   60 k |     0.28 |  0.142 | 351.4 k | 
| LE1080 | 100k    | 72514-3 |  100 k |     0.27 |  0.128 | 367.0 k |
| LE1080 | 1M      | 17861-6 |   25 k |     2.61 |  0.208 | 383.1 k | 
| LE1080 | 1M      | 8310-5  |  603 k |     2.68 |  0.201 | 372.8 k | 
| LE1080 | 1M      | 72514-3 |  998 k |     2.82 |  0.192 | 354.9 k |

The evaluation of patient based measures doesn't depend on the number of hits (patients). The time needed to evaluate a CQL expression over all patients only depends on the total number of patients. The measurements show that Blaze can evaluate about 350 k Patients per second.

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
  exists [Observation: "body-weight"] O where O.value < 75.3 'kg'
```

The CQL query is executed with the following `blazectl` command:

```sh
blazectl evaluate-measure "cql/observation-$CODE-$VALUE.yml" --server http://localhost:8080/fhir | jq -rf cql/result.jq
```

| System | Dataset | Code    |   Value | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|---------|--------:|-------:|---------:|-------:|--------:|
| LE1080 | 100k    | 29463-7 | 13.6 kg |   10 k |     0.68 |  0.031 | 146.9 k | 
| LE1080 | 100k    | 29463-7 | 75.3 kg |   50 k |     0.51 |  0.033 | 197.1 k | 
| LE1080 | 100k    | 29463-7 |  185 kg |  100 k |     0.30 |  0.106 | 331.6 k |
| LE1080 | 1M      | 29463-7 | 13.6 kg |   99 k |   151.05 |  4.674 |   6.6 k | 
| LE1080 | 1M      | 29463-7 | 75.3 kg |  500 k |   104.68 |  2.022 |   9.6 k | 
| LE1080 | 1M      | 29463-7 |  185 kg |  998 k |     3.19 |  0.176 | 313.8 k |
