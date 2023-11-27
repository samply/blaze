# CQL Performance

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache ¹ |
|--------|----------|------------|------:|--------:|-------:|---------:|------------:|-----------------:|
| LEA25  | on-prem  | EPYC 7543P |     4 |  32 GiB |   2 TB |    8 GiB |       8 GiB |            2.5 M | 
| LEA36  | on-prem  | EPYC 7543P |     8 |  64 GiB |   2 TB |   16 GiB |      16 GiB |              5 M | 
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB |   2 TB |   32 GiB |      32 GiB |             10 M | 
| LEA58  | on-prem  | EPYC 7543P |    32 | 256 GiB |   2 TB |   64 GiB |      64 GiB |             20 M | 

¹ Size of the resource cache (DB_RESOURCE_CACHE_SIZE)

## Datasets

The following datasets were used:

| Dataset | # Pat. ¹ | # Res. ² | # Obs. ³ |
|---------|---------:|---------:|---------:|
| 100k    |    100 k |    104 M |     59 M |
| 100k-fh |    100 k |    317 M |    191 M |
| 1M      |      1 M |   1044 M |    593 M |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

## Simple Code Search

In this section, CQL Queries for selecting Patients which have Observations with a certain codes are analyzed. The codes were chosen to produce a wide range of hits (number of matching patients). The hits are 2 k, 60 k and 100 k (all patients match).

![](cql/simple-code-search-100k.png) 

The bar chart shows the number of patients a system can process per second. The metric Patients/s is used over all different variants of searches analyzed here. It can be used to decide whether a systems performance is sufficient to perform query evaluation in a given time budget. For example if the value of Patients/s is 200 k this means that the evaluation of 100 k patients takes half a second.

For the simple code search, the performance declines with the number of hits observed and raises with the system size.

### Data

| Dataset | System | Code    | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|---------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  | 17861-6 |    2 k |     0.28 |  0.014 | 358.5 k | 
| 100k    | LEA25  | 8310-5  |   60 k |     0.38 |  0.024 | 262.9 k | 
| 100k    | LEA25  | 72514-3 |  100 k |     0.47 |  0.023 | 211.9 k |
| 100k    | LEA36  | 17861-6 |    2 k |     0.19 |  0.006 | 521.0 k | 
| 100k    | LEA36  | 8310-5  |   60 k |     0.25 |  0.007 | 401.1 k | 
| 100k    | LEA36  | 72514-3 |  100 k |     0.28 |  0.007 | 359.8 k |
| 100k    | LEA47  | 17861-6 |    2 k |     0.14 |  0.004 | 730.9 k | 
| 100k    | LEA47  | 8310-5  |   60 k |     0.17 |  0.003 | 587.7 k | 
| 100k    | LEA47  | 72514-3 |  100 k |     0.19 |  0.004 | 535.2 k |
| 100k    | LEA58  | 17861-6 |    2 k |     0.11 |  0.003 | 901.8 k | 
| 100k    | LEA58  | 8310-5  |   60 k |     0.13 |  0.003 | 769.0 k | 
| 100k    | LEA58  | 72514-3 |  100 k |     0.14 |  0.001 | 727.3 k |
| 100k-fh | LEA58  | 17861-6 |    3 k |     0.20 |  0.003 | 495.0 k |
| 100k-fh | LEA58  | 8310-5  |   98 k |     0.21 |  0.002 | 474.6 k |
| 100k-fh | LEA58  | 72514-3 |  100 k |     0.21 |  0.003 | 479.4 k |
| 1M      | LEA47  | 17861-6 |   25 k |     0.93 |  0.003 |   1.1 M | 
| 1M      | LEA47  | 8310-5  |  603 k |     1.24 |  0.009 | 808.6 k | 
| 1M      | LEA47  | 72514-3 |  998 k |     1.43 |  0.007 | 698.1 k |
| 1M      | LEA58  | 17861-6 |   25 k |     0.87 |  0.006 |   1.1 M |
| 1M      | LEA58  | 8310-5  |  603 k |     1.03 |  0.003 | 972.6 k | 
| 1M      | LEA58  | 72514-3 |  998 k |     1.14 |  0.005 | 873.6 k |

### CQL Query

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

## Code and Value Search

In this section, CQL Queries for selecting Patients which have Observations with a certain code and value are analyzed. The values were chosen to produce a wide range of hits (number of matching patients). The hits are 10 k, 50 k and 100 k (all patients match).

![](cql/code-value-search-100k.png)

The bar chart shows the number of patients a system can process per second as described above. The performance raises with the number of hits and system size. Especially the query matching all patients is nearly as fast as the simple code search because finding a match in the first Observation is faster than having to go through multiple non-matching Observations. This means, for code and value search finding lesser patients is costlier than finding more. 

### Data

| Dataset | System | Code    |   Value | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|---------|--------:|-------:|---------:|-------:|--------:|
| 100k    | LEA25  | 29463-7 | 13.6 kg |   10 k |     2.71 |  0.026 |  36.9 k | 
| 100k    | LEA25  | 29463-7 | 75.3 kg |   50 k |     1.70 |  0.014 |  58.7 k | 
| 100k    | LEA25  | 29463-7 |  185 kg |  100 k |     0.53 |  0.030 | 187.2 k |
| 100k    | LEA36  | 29463-7 | 13.6 kg |   10 k |     1.34 |  0.015 |  74.9 k | 
| 100k    | LEA36  | 29463-7 | 75.3 kg |   50 k |     1.04 |  0.012 |  96.2 k | 
| 100k    | LEA36  | 29463-7 |  185 kg |  100 k |     0.34 |  0.012 | 295.3 k |
| 100k    | LEA47  | 29463-7 | 13.6 kg |   10 k |     0.78 |  0.009 | 127.6 k | 
| 100k    | LEA47  | 29463-7 | 75.3 kg |   50 k |     0.58 |  0.005 | 171.4 k | 
| 100k    | LEA47  | 29463-7 |  185 kg |  100 k |     0.21 |  0.005 | 486.5 k |
| 100k    | LEA58  | 29463-7 | 13.6 kg |   10 k |     0.47 |  0.006 | 213.2 k |  
| 100k    | LEA58  | 29463-7 | 75.3 kg |   50 k |     0.37 |  0.004 | 271.3 k | 
| 100k    | LEA58  | 29463-7 |  185 kg |  100 k |     0.15 |  0.003 | 671.8 k |
| 100k-fh | LEA58  | 29463-7 | 13.6 kg |  100 k |     0.38 |  0.005 | 260.3 k |  
| 100k-fh | LEA58  | 29463-7 | 75.3 kg |  100 k |     0.30 |  0.004 | 336.2 k | 
| 100k-fh | LEA58  | 29463-7 |  185 kg |  100 k |     0.24 |  0.003 | 417.0 k |
| 1M      | LEA47  | 29463-7 | 13.6 kg |   99 k |    91.49 |  1.195 |  10.9 k | 
| 1M      | LEA47  | 29463-7 | 75.3 kg |  500 k |    10.66 |  0.851 |  93.8 k | 
| 1M      | LEA47  | 29463-7 |  185 kg |  998 k |     1.50 |  0.010 | 665.1 k |
| 1M      | LEA58  | 29463-7 | 13.6 kg |   99 k |     4.60 |  0.016 | 217.4 k |  
| 1M      | LEA58  | 29463-7 | 75.3 kg |  500 k |     3.16 |  0.014 | 316.0 k | 
| 1M      | LEA58  | 29463-7 |  185 kg |  998 k |     1.28 |  0.007 | 781.0 k |

### CQL Query

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

## Code, Date and Age Search

In this section, CQL Queries for selecting Patients which have Observation resources with code 718-7 (Hemoglobin), date between 2015 and 2019 and age of patient at observation date below 18.

![](cql/code-date-age-search-100k.png)

### Data

| Dataset | System | Code       | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|------------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  | hemoglobin |   20 k |     0.78 |  0.012 | 127.8 k |
| 100k    | LEA25  | calcium    |   20 k |     3.10 |  0.037 |  32.3 k |
| 100k    | LEA36  | hemoglobin |   20 k |     0.47 |  0.017 | 214.3 k |
| 100k    | LEA36  | calcium    |   20 k |     1.62 |  0.010 |  61.8 k |
| 100k    | LEA47  | hemoglobin |   20 k |     0.28 |  0.004 | 357.3 k |
| 100k    | LEA47  | calcium    |   20 k |     0.95 |  0.009 | 105.7 k |
| 100k    | LEA58  | hemoglobin |   20 k |     0.20 |  0.003 | 507.9 k |
| 100k    | LEA58  | calcium    |   20 k |     0.63 |  0.008 | 158.6 k |
| 100k-fh | LEA58  | hemoglobin |   20 k |     0.49 |  0.003 | 204.1 k |
| 100k-fh | LEA58  | calcium    |   20 k |     1.55 |  0.015 |  64.6 k |
| 1M      | LEA47  | hemoglobin |  200 k |     2.99 |  0.026 | 334.6 k |
| 1M      | LEA47  | calcium    |  199 k |   120.68 |  1.678 |   8.3 k |
| 1M      | LEA58  | hemoglobin |  200 k |     1.74 |  0.001 | 574.5 k |
| 1M      | LEA58  | calcium    |  199 k |     6.29 |  0.033 | 159.0 k |

### CQL Query

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

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    9 k |     0.45 |  0.015 | 222.5 k |
| 100k    | LEA36  |    9 k |     0.31 |  0.007 | 327.8 k |
| 100k    | LEA47  |    9 k |     0.20 |  0.002 | 489.8 k |
| 100k    | LEA58  |    9 k |     0.17 |  0.004 | 596.6 k |
| 100k-fh | LEA58  |    9 k |     0.40 |  0.002 | 248.5 k |
| 1M      | LEA47  |   87 k |     1.09 |  0.005 | 918.4 k |
| 1M      | LEA58  |   87 k |     1.14 |  0.007 | 880.0 k |

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

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |   95 k |     0.83 |  0.022 | 120.2 k |
| 100k    | LEA36  |   95 k |     0.54 |  0.009 | 186.0 k |
| 100k    | LEA47  |   95 k |     0.35 |  0.007 | 283.3 k |
| 100k    | LEA58  |   95 k |     0.26 |  0.005 | 391.2 k |
| 100k-fh | LEA58  |   98 k |     0.35 |  0.001 | 284.9 k |
| 1M      | LEA47  |  954 k |     1.80 |  0.008 | 554.5 k |
| 1M      | LEA58  |  954 k |     1.81 |  0.012 | 552.2 k |

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

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    395 |     1.75 |  0.014 |  57.2 k |
| 100k    | LEA36  |    395 |     1.15 |  0.014 |  87.0 k |
| 100k    | LEA47  |    395 |     0.78 |  0.005 | 128.2 k |
| 100k    | LEA58  |    395 |     0.56 |  0.024 | 177.3 k |
| 100k-fh | LEA58  |    2 k |     1.63 |  0.006 |  61.2 k |
| 1M      | LEA47  |    4 k |     2.65 |  0.014 | 377.9 k |
| 1M      | LEA58  |    4 k |     3.38 |  0.013 | 295.5 k |

## 50 Rare Code Search

```sh
cql/search.sh condition-50-rare
```

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |   15 k |     7.44 |  0.103 |  13.4 k |
| 100k    | LEA36  |   15 k |     5.03 |  0.065 |  19.9 k |
| 100k    | LEA47  |   15 k |     3.28 |  0.127 |  30.4 k |
| 100k    | LEA58  |   15 k |     2.85 |  0.156 |  35.1 k |
| 100k-fh | LEA58  |   16 k |     6.84 |  0.018 |  14.6 k |
| 1M      | LEA47  |  155 k |     9.35 |  0.047 | 106.9 k |
| 1M      | LEA58  |  155 k |    13.44 |  0.068 |  74.4 k |

## All Code Search

```sh
cql/search.sh condition-all
```

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |   99 k |     3.25 |  0.014 |  30.8 k |
| 100k    | LEA36  |   99 k |     2.20 |  0.031 |  45.4 k |
| 100k    | LEA47  |   99 k |     1.42 |  0.068 |  70.7 k |
| 100k    | LEA58  |   99 k |     1.01 |  0.068 |  99.0 k |
| 100k-fh | LEA58  |  100 k |     1.55 |  0.006 |  64.7 k |
| 1M      | LEA47  |  995 k |     4.75 |  0.014 | 210.5 k |
| 1M      | LEA58  |  995 k |     6.10 |  0.027 | 164.0 k |

## Inpatient Stress Search

```sh
cql/search.sh inpatient-stress
```

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    2 k |     5.33 |  0.036 |  18.8 k |
| 100k    | LEA36  |    2 k |     3.37 |  0.019 |  29.7 k |
| 100k    | LEA47  |    2 k |     2.02 |  0.008 |  49.5 k |
| 100k    | LEA58  |    2 k |     1.29 |  0.012 |  77.4 k |
| 100k-fh | LEA58  |    2 k |     4.41 |  0.041 |  22.7 k |
| 1M      | LEA58  |   16 k |    11.10 |  0.046 |  90.0 k |
