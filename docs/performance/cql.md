# CQL Performance

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache ¹ |
|--------|----------|------------|------:|--------:|-------:|---------:|------------:|-----------------:|
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB |   2 TB |   32 GiB |      16 GiB |             10 M | 
| LEA58  | on-prem  | EPYC 7543P |    32 | 256 GiB |   2 TB |   64 GiB |      32 GiB |             20 M | 

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
| LEA47  | 100k    | 17861-6 |    2 k |     0.26 |  0.158 | 384.5 k | 
| LEA47  | 100k    | 8310-5  |   60 k |     0.28 |  0.142 | 351.4 k | 
| LEA47  | 100k    | 72514-3 |  100 k |     0.27 |  0.128 | 367.0 k |
| LEA47  | 1M      | 17861-6 |   25 k |     2.61 |  0.208 | 383.1 k | 
| LEA47  | 1M      | 8310-5  |  603 k |     2.68 |  0.201 | 372.8 k | 
| LEA47  | 1M      | 72514-3 |  998 k |     2.82 |  0.192 | 354.9 k |
| LEA58  | 1M      | 17861-6 |   25 k |     2.87 |  0.291 | 348.5 k |
| LEA58  | 1M      | 8310-5  |  603 k |     3.02 |  0.257 | 330.8 k | 
| LEA58  | 1M      | 72514-3 |  998 k |     3.06 |  0.426 | 326.7 k |

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
| LEA47  | 100k    | 29463-7 | 13.6 kg |   10 k |     0.68 |  0.031 | 146.9 k | 
| LEA47  | 100k    | 29463-7 | 75.3 kg |   50 k |     0.51 |  0.033 | 197.1 k | 
| LEA47  | 100k    | 29463-7 |  185 kg |  100 k |     0.30 |  0.106 | 331.6 k |
| LEA47  | 1M      | 29463-7 | 13.6 kg |   99 k |   151.05 |  4.674 |   6.6 k | 
| LEA47  | 1M      | 29463-7 | 75.3 kg |  500 k |   104.68 |  2.022 |   9.6 k | 
| LEA47  | 1M      | 29463-7 |  185 kg |  998 k |     3.19 |  0.176 | 313.8 k |
| LEA58  | 1M      | 29463-7 | 13.6 kg |   99 k |     8.24 |  0.072 | 121.4 k | 
| LEA58  | 1M      | 29463-7 | 75.3 kg |  500 k |     6.59 |  0.140 | 151.8 k | 
| LEA58  | 1M      | 29463-7 |  185 kg |  998 k |     3.04 |  0.209 | 329.0 k |

## Code and Value Search

In this section, CQL Queries for selecting Patients which have Observation resources with code 718-7 (Hemoglobin), date between 2015 and 2019 and age of patient at observation date below 18.

```text
library "hemoglobin-date-age"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  exists [Observation: Code '718-7' from loinc] O
  where year from O.effective between 2015 and 2019
  and AgeInYearsAt(O.effective) < 18
```

The CQL query is executed with the following `blazectl` command:

```sh
blazectl evaluate-measure "cql/hemoglobin-date-age.yml" --server http://localhost:8080/fhir | jq -rf cql/result.jq
```

| System | Dataset | Code       | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|------------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | hemoglobin |   20 k |     0.35 |  0.034 | 286.5 k |
| LEA47  | 100k    | calcium    |   20 k |     1.50 |  0.035 |  66.6 k |
| LEA47  | 1M      | hemoglobin |  200 k |     4.79 |  0.119 | 208.8 k |
| LEA47  | 1M      | calcium    |  199 k |   182.90 |  3.900 |   5.5 k |
| LEA58  | 1M      | hemoglobin |  200 k |     3.55 |  0.038 | 281.8 k |
| LEA58  | 1M      | calcium    |  199 k |    10.10 |  0.033 |  99.0 k |
