# CQL Performance

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache ¹ |
|--------|----------|------------|------:|--------:|-------:|---------:|------------:|-----------------:|
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB |   2 TB |   32 GiB |      32 GiB |             10 M | 
| LEA58  | on-prem  | EPYC 7543P |    32 | 256 GiB |   2 TB |   64 GiB |      64 GiB |             20 M | 

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
cql/search.sh observation-17861-6
cql/search.sh observation-8310-5
cql/search.sh observation-72514-3
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | 17861-6 |    2 k |     0.05 |  0.000 |   1.9 M | 
| LEA47  | 100k    | 8310-5  |   60 k |     0.08 |  0.001 |   1.3 M | 
| LEA47  | 100k    | 72514-3 |  100 k |     0.11 |  0.004 | 914.2 k |
| LEA47  | 1M      | 17861-6 |   25 k |     0.48 |  0.003 |   2.1 M | 
| LEA47  | 1M      | 8310-5  |  603 k |     0.70 |  0.009 |   1.4 M | 
| LEA47  | 1M      | 72514-3 |  998 k |     0.99 |  0.005 |   1.0 M |
| LEA58  | 1M      | 17861-6 |   25 k |     0.51 |  0.004 |   2.0 M |
| LEA58  | 1M      | 8310-5  |  603 k |     0.51 |  0.004 |   2.0 M | 
| LEA58  | 1M      | 72514-3 |  998 k |     0.58 |  0.002 |   1.7 M |

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
cql/search.sh observation-body-weight-10
cql/search.sh observation-body-weight-50
cql/search.sh observation-body-weight-100
```

| System | Dataset | Code    |   Value | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|---------|--------:|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | 29463-7 | 13.6 kg |   10 k |     0.06 |  0.001 |   1.8 M | 
| LEA47  | 100k    | 29463-7 | 75.3 kg |   50 k |     0.09 |  0.003 |   1.1 M | 
| LEA47  | 100k    | 29463-7 |  185 kg |  100 k |     0.12 |  0.002 | 806.6 k |
| LEA47  | 1M      | 29463-7 | 13.6 kg |   99 k |     0.49 |  0.001 |   2.0 M | 
| LEA47  | 1M      | 29463-7 | 75.3 kg |  500 k |     0.84 |  0.006 |   1.2 M | 
| LEA47  | 1M      | 29463-7 |  185 kg |  998 k |     1.16 |  0.009 | 865.8 k |
| LEA58  | 1M      | 29463-7 | 13.6 kg |   99 k |     0.50 |  0.004 |   2.0 M | 
| LEA58  | 1M      | 29463-7 | 75.3 kg |  500 k |     0.53 |  0.003 |   1.9 M | 
| LEA58  | 1M      | 29463-7 |  185 kg |  998 k |     0.72 |  0.004 |   1.4 M |

## Code, Date and Age Search

In this section, CQL Queries for selecting Patients which have Observation resources with code 718-7 (Hemoglobin), date between 2015 and 2019 and age of patient at observation date below 18.

```text
library "hemoglobin-date-age"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  exists [Observation: Code '718-7' from loinc] O
  where year from (O.effective as dateTime) between 2015 and 2019
  and AgeInYearsAt(O.effective as dateTime) < 18
```

The CQL query is executed with the following `blazectl` command:

```sh
cql/search.sh hemoglobin-date-age
cql/search.sh calcium-date-age
```

| System | Dataset | Code       | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|------------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | hemoglobin |   20 k |     0.06 |  0.003 |   1.7 M |
| LEA47  | 100k    | calcium    |   20 k |     0.08 |  0.002 |   1.2 M |
| LEA47  | 1M      | hemoglobin |  200 k |     0.50 |  0.005 |   2.0 M |
| LEA47  | 1M      | calcium    |  199 k |     0.73 |  0.003 |   1.4 M |
| LEA58  | 1M      | hemoglobin |  200 k |     0.51 |  0.001 |   2.0 M |
| LEA58  | 1M      | calcium    |  199 k |     0.52 |  0.009 |   1.9 M |

## Double Code Search

In this section, CQL Queries for selecting Patients which have Condition resources with one of two codes used.

```text
library "condition-two"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'
code fever: '386661006' from sct
code cough: '49727002' from sct

context Patient

define InInitialPopulation:
  exists [Condition: fever] or 
  exists [Condition: cough]
```

```sh
cql/search.sh condition-two
```

| System | Dataset | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    |    9 k |     0.05 |  0.001 |   1.9 M |
| LEA47  | 1M      |   87 k |     0.50 |  0.002 |   2.0 M |
| LEA58  | 1M      |   87 k |     0.52 |  0.002 |   1.9 M |

## Ten Frequent Code Search

```text
library "condition-ten-frequent"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'

context Patient

define InInitialPopulation:
  exists [Condition: Code '444814009' from sct] or
  exists [Condition: Code '840544004' from sct] or
  exists [Condition: Code '840539006' from sct] or
  exists [Condition: Code '386661006' from sct] or
  exists [Condition: Code '195662009' from sct] or
  exists [Condition: Code '49727002' from sct] or
  exists [Condition: Code '10509002' from sct] or
  exists [Condition: Code '72892002' from sct] or
  exists [Condition: Code '36955009' from sct] or
  exists [Condition: Code '162864005' from sct]
```

```sh
cql/search.sh condition-ten-frequent
```

| System | Dataset | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    |   95 k |     0.10 |  0.004 | 991.1 k |
| LEA47  | 1M      |  954 k |     0.88 |  0.004 |   1.1 M |
| LEA58  | 1M      |  954 k |     0.56 |  0.001 |   1.8 M |

## Ten Rare Code Search

```text
library "condition-ten-rare"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'

context Patient

define InInitialPopulation:
  exists [Condition: Code '62718007' from sct] or
  exists [Condition: Code '234466008' from sct] or
  exists [Condition: Code '288959006' from sct] or
  exists [Condition: Code '47505003' from sct] or
  exists [Condition: Code '698754002' from sct] or
  exists [Condition: Code '157265008' from sct] or
  exists [Condition: Code '15802004' from sct] or
  exists [Condition: Code '14760008' from sct] or
  exists [Condition: Code '36923009' from sct] or
  exists [Condition: Code '45816000' from sct]
```

```sh
cql/search.sh condition-ten-rare
```

| System | Dataset | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    |    0 k |     0.05 |  0.001 |   1.9 M |
| LEA47  | 1M      |    4 k |     0.51 |  0.002 |   2.0 M |
| LEA58  | 1M      |    4 k |     0.49 |  0.003 |   2.0 M |

## 50 Rare Code Search

```sh
cql/search.sh condition-50-rare
```

| System | Dataset | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    |   15 k |     0.10 |  0.001 |   1.0 M |
| LEA47  | 1M      |  155 k |     1.05 |  0.011 | 953.8 k |
| LEA58  | 1M      |  155 k |     0.54 |  0.003 |   1.8 M |

## All Code Search

```sh
cql/search.sh condition-all
```

| System | Dataset | # Hits | Time (s) | StdDev |  Pat./s |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    |   99 k |     0.12 |  0.002 | 839.1 k |
| LEA47  | 1M      |  995 k |     1.09 |  0.002 | 913.2 k |
| LEA58  | 1M      |  995 k |     0.66 |  0.006 |   1.5 M |
