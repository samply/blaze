# CQL Performance

## TL;DR

For the CQL queries analyzed here, the relative performance of query evaluation in patients/s stays the same for datasets with 1 million patients compared to datasets with 100 thousand patients if the system resources are sufficient and Blaze performs equally or better for datasets with more patient history. In all cases the performance increases with rising system resources.  

## Systems

The following systems with rising resources were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |  SSD | Heap Mem ¹ | Block Cache ² | Resource Cache ³ |
|--------|----------|------------|------:|--------:|-----:|-----------:|--------------:|-----------------:|
| LEA25  | on-prem  | EPYC 7543P |     4 |  32 GiB | 2 TB |      8 GiB |         8 GiB |            2.5 M | 
| LEA36  | on-prem  | EPYC 7543P |     8 |  64 GiB | 2 TB |     16 GiB |        16 GiB |              5 M | 
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB | 2 TB |     32 GiB |        32 GiB |             10 M | 
| LEA58  | on-prem  | EPYC 7543P |    32 | 256 GiB | 2 TB |     64 GiB |        64 GiB |             20 M | 

¹ Size of the Java Heap (`JAVA_TOOL_OPTIONS`)
² Size of the block cache (`DB_BLOCK_CACHE_SIZE`)
³ Size of the resource cache (`DB_RESOURCE_CACHE_SIZE`)

All systems have in common that the heap mem and the block cache both use 1/4 of the total available memory each. So the Blaze process itself will only use about half the system memory available. The rest of the system memory will be used as file system cache. 

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | SSD Size |
|---------|----------|---------:|---------:|---------:|---------:|
| 100k    | 10 years |    100 k |    104 M |     59 M |  202 GiB |
| 100k-fh | full     |    100 k |    317 M |    191 M |  323 GiB |
| 1M      | 10 years |      1 M |   1044 M |    593 M | 1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

The creation of the datasets is described in the [Synthea section](./synthea/README.md). The disc size is measured after full manual compaction of the database. The actual disc size will be up to 50% higher, depending on the state of compaction which happens regularly in the background.

## Metric

The metric analyzed here are the number of patients a system can process per second. It was chosen because the CQL evaluation performance depends heavily on the number of patients available in Blaze. The datasets contain either 100 k or 1 million patients in order to represent two relevant sizes from where an interpolation or extrapolation towards the target size should be possible. The metric patients per second itself is independent from the actual number of patients and can therefore be used to compare the two population sizes analysed here.

With a given patients per second value, its always possible to calculate the to be expected CQL evaluation duration by dividing the target systems number of patients by that number. So for example, if the metric is 100 k patients/s Blaze will need 1 second if it contains 100 k patients and 5 seconds if it contains 500 k patients.

## Simple Code Search

In this section, CQL queries for selecting patients which have observations with a certain code are analyzed. The codes were chosen to produce a wide range of hits (number of matching patients). For the 100k dataset the hits are 2 k, 60 k and 100 k, for the 100k-fh dataset the hits are 2 k, 57 k and 100 k and for the 1M dataset the hits are 25 k, 603 k and 998 k.

![](cql/simple-code-search-100k.png)

The first chart shows the results for the 100k dataset. It shows that the performance raises with the system size and declines a bit with the number of patients found. This decline can be explained because finding a hit is a two stage process. First hits are found in the [CompartmentSearchParamValueResource Index](../implementation/database.md#CompartmentSearchParamValueResource) which will also contain historic matches that have to be further checked using the [ResourceAsOf Index](../implementation/database.md#ResourceAsOf).

![](cql/simple-code-search-100k-fh.png) 

The second bar chart shows the results for the 100k-fh dataset which differs by the 100 k dataset in that it contains a full history of patient data instead of a history capped at 10 years. Especially the number of observations is 191 M compared to only 59 M in the 100k dataset. Comparing the two bar charts, the performance is nearly identical. So for simple code search, the performance doesn't depend on the amount of patient history. 

![](cql/simple-code-search-1M.png)

The third bar chart shows the results for the 1M dataset. For the two bigger systems LEA47 and LEA58, the relative performance measured in patients per second is identical to the performance Blaze shows at the smaller datasets with only 100 k patients. However the same can't be said for the two smaller systems LEA25 and LEA36, were the relative performance suffers due to memory limitations of that systems. 

### Charts Comparing the Datasets

#### System - LEA25

![](cql/simple-code-search-all-datasets-LEA25.png)

For the LEA25 system, the dataset with 1 million patients is to big, because the relative performance suffers compared to both the smaller datasets.

#### System - LEA36

![](cql/simple-code-search-all-datasets-LEA36.png)

With a bit lesser severity, the same can be said for the LEA36 system. It is to small for the 1M dataset.

#### System - LEA47

![](cql/simple-code-search-all-datasets-LEA47.png)

For the LEA47 system, the relative performance is the same for all datasets.

#### System - LEA58

![](cql/simple-code-search-all-datasets-LEA58.png)

The same can be said for the LEA58 system.

### Data

| Dataset | System | Code    | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|---------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  | 17861-6 |    2 k |     0.08 |  0.005 | 1.203 M | 
| 100k    | LEA25  | 8310-5  |   60 k |     0.29 |  0.008 | 342.8 k | 
| 100k    | LEA25  | 72514-3 |  100 k |     0.42 |  0.008 | 237.7 k |
| 100k    | LEA36  | 17861-6 |    2 k |     0.05 |  0.001 | 2.000 M | 
| 100k    | LEA36  | 8310-5  |   60 k |     0.15 |  0.003 | 667.8 k | 
| 100k    | LEA36  | 72514-3 |  100 k |     0.23 |  0.005 | 442.2 k |
| 100k    | LEA47  | 17861-6 |    2 k |     0.05 |  0.002 | 1.873 M | 
| 100k    | LEA47  | 8310-5  |   60 k |     0.09 |  0.004 | 1.104 M | 
| 100k    | LEA47  | 72514-3 |  100 k |     0.14 |  0.004 | 731.9 k |
| 100k    | LEA58  | 17861-6 |    2 k |     0.05 |  0.002 | 1.845 M | 
| 100k    | LEA58  | 8310-5  |   60 k |     0.08 |  0.001 | 1.315 M | 
| 100k    | LEA58  | 72514-3 |  100 k |     0.09 |  0.002 | 1.116 M |
| 100k-fh | LEA25  | 788-0   |    2 k |     0.08 |  0.005 | 1.299 M |
| 100k-fh | LEA25  | 44261-6 |   57 k |     0.26 |  0.017 | 379.7 k |
| 100k-fh | LEA25  | 72514-3 |  100 k |     0.40 |  0.025 | 250.9 k |
| 100k-fh | LEA36  | 788-0   |    2 k |     0.05 |  0.001 | 1.854 M |
| 100k-fh | LEA36  | 44261-6 |   57 k |     0.13 |  0.004 | 756.1 k |
| 100k-fh | LEA36  | 72514-3 |  100 k |     0.20 |  0.003 | 489.4 k |
| 100k-fh | LEA47  | 788-0   |    2 k |     0.05 |  0.001 | 1.938 M |
| 100k-fh | LEA47  | 44261-6 |   57 k |     0.08 |  0.003 | 1.332 M |
| 100k-fh | LEA47  | 72514-3 |  100 k |     0.09 |  0.002 | 1.100 M |
| 100k-fh | LEA58  | 788-0   |    2 k |     0.05 |  0.002 | 1.930 M |
| 100k-fh | LEA58  | 44261-6 |   57 k |     0.07 |  0.001 | 1.385 M |
| 100k-fh | LEA58  | 72514-3 |  100 k |     0.09 |  0.001 | 1.161 M |
| 1M      | LEA25  | 17861-6 |   25 k |     0.47 |  0.011 | 2.148 M | 
| 1M      | LEA25  | 8310-5  |  603 k |    10.69 |  1.232 |  93.6 k | 
| 1M      | LEA25  | 72514-3 |  998 k |    16.74 |  1.959 |  59.8 k |
| 1M      | LEA36  | 17861-6 |   25 k |     0.44 |  0.003 | 2.283 M | 
| 1M      | LEA36  | 8310-5  |  603 k |     4.61 |  0.031 | 217.0 k | 
| 1M      | LEA36  | 72514-3 |  998 k |     7.15 |  0.018 | 139.8 k |
| 1M      | LEA47  | 17861-6 |   25 k |     0.47 |  0.004 | 2.138 M | 
| 1M      | LEA47  | 8310-5  |  603 k |     0.64 |  0.008 | 1.555 M | 
| 1M      | LEA47  | 72514-3 |  998 k |     0.97 |  0.009 | 1.032 M |
| 1M      | LEA58  | 17861-6 |   25 k |     0.48 |  0.005 | 2.069 M |
| 1M      | LEA58  | 8310-5  |  603 k |     0.63 |  0.004 | 1.587 M | 
| 1M      | LEA58  | 72514-3 |  998 k |     0.73 |  0.006 | 1.375 M |

### Example CQL Query

```text
library "observation-17861-6"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  exists [Observation: Code '17861-6' from loinc]
```

The CQL queries can be executed with the following `blazectl` commands:

```sh
cql/search.sh observation-17861-6
cql/search.sh observation-8310-5
cql/search.sh observation-72514-3
cql/search.sh observation-788-0
cql/search.sh observation-44261-6
```

## Code and Value Search

In this section, CQL Queries for selecting patients which have observations with a certain code and value are analyzed. The values were chosen to produce a wide range of hits (number of matching patients). The hits are 10 k, 50 k and 100 k.

![](cql/code-value-search-100k.png)

The first chart shows the results for the 100k dataset. It shows the number of patients a system can process per second as described above. The performance raises with the number of hits and system size. Especially the query matching all patients is nearly as fast as the simple code search because finding a match in the first observation is faster than having to go through multiple non-matching observations. This means, for code and value search finding lesser patients is costlier than finding more. 

![](cql/code-value-search-100k-fh.png)

The second chart shows the results for the 100k-fh dataset. The queries used in the analysis against the 100k-fh dataset were the same as used for the other datasets. The number of hits however were always 100 k, because with full history also data from the patients childhood is included. Nevertheless, the performance using the same queries is nearly the same in the < 185 kg case and even better for the smaller values. 

![](cql/code-value-search-1M.png)

The third chart shows the results for the 1M dataset. Here only the biggest system, the LEA58, can preserve the relative performance figures. All other systems suffer from memory restrictions.

### Charts Comparing the Datasets

#### System - LEA25

![](cql/code-value-search-all-datasets-LEA25.png)

For the LEA25 system, the dataset with 1 million patients is to big, because the relative performance suffers compared to both the smaller datasets.

#### System - LEA36

![](cql/code-value-search-all-datasets-LEA36.png)

With a bit lesser severity, the same can be said for the LEA36 system. It is to small for the 1M dataset.

#### System - LEA47

![](cql/code-value-search-all-datasets-LEA47.png)

With a lesser severity, the same can be said for the LEA47 system. It is to small for the 1M dataset.

#### System - LEA58

![](cql/code-value-search-all-datasets-LEA58.png)

For the LEA58 system, the relative performance is the same for all datasets.

### Data

| Dataset | System | Code    |   Value | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|---------|--------:|-------:|---------:|-------:|--------:|
| 100k    | LEA25  | 29463-7 | 13.6 kg |   10 k |     0.35 |  0.019 | 286.1 k | 
| 100k    | LEA25  | 29463-7 | 75.3 kg |   50 k |     0.84 |  0.025 | 118.8 k | 
| 100k    | LEA25  | 29463-7 |  185 kg |  100 k |     1.23 |  0.022 |  81.1 k |
| 100k    | LEA36  | 29463-7 | 13.6 kg |   10 k |     0.14 |  0.007 | 698.5 k | 
| 100k    | LEA36  | 29463-7 | 75.3 kg |   50 k |     0.36 |  0.011 | 275.7 k | 
| 100k    | LEA36  | 29463-7 |  185 kg |  100 k |     0.57 |  0.016 | 176.4 k |
| 100k    | LEA47  | 29463-7 | 13.6 kg |   10 k |     0.08 |  0.007 | 1.252 M | 
| 100k    | LEA47  | 29463-7 | 75.3 kg |   50 k |     0.19 |  0.004 | 519.2 k | 
| 100k    | LEA47  | 29463-7 |  185 kg |  100 k |     0.35 |  0.016 | 286.2 k |
| 100k    | LEA58  | 29463-7 | 13.6 kg |   10 k |     0.07 |  0.002 | 1.409 M |  
| 100k    | LEA58  | 29463-7 | 75.3 kg |   50 k |     0.13 |  0.004 | 780.9 k | 
| 100k    | LEA58  | 29463-7 |  185 kg |  100 k |     0.18 |  0.006 | 543.8 k |
| 100k-fh | LEA25  | 29463-7 | 13.6 kg |  100 k |     6.40 |  0.072 |  15.6 k |  
| 100k-fh | LEA25  | 29463-7 | 75.3 kg |  100 k |     3.23 |  0.037 |  31.0 k | 
| 100k-fh | LEA25  | 29463-7 |  185 kg |  100 k |     1.18 |  0.017 |  84.7 k |
| 100k-fh | LEA36  | 29463-7 | 13.6 kg |  100 k |     2.45 |  0.023 |  40.8 k |  
| 100k-fh | LEA36  | 29463-7 | 75.3 kg |  100 k |     1.27 |  0.020 |  78.6 k | 
| 100k-fh | LEA36  | 29463-7 |  185 kg |  100 k |     0.50 |  0.005 | 199.6 k |
| 100k-fh | LEA47  | 29463-7 | 13.6 kg |  100 k |     0.78 |  0.021 | 128.3 k |  
| 100k-fh | LEA47  | 29463-7 | 75.3 kg |  100 k |     0.45 |  0.006 | 221.1 k | 
| 100k-fh | LEA47  | 29463-7 |  185 kg |  100 k |     0.17 |  0.005 | 572.4 k |
| 100k-fh | LEA58  | 29463-7 | 13.6 kg |  100 k |     0.74 |  0.022 | 134.4 k |  
| 100k-fh | LEA58  | 29463-7 | 75.3 kg |  100 k |     0.43 |  0.007 | 234.5 k | 
| 100k-fh | LEA58  | 29463-7 |  185 kg |  100 k |     0.18 |  0.004 | 565.8 k |
| 1M      | LEA25  | 29463-7 | 13.6 kg |   99 k |     2.91 |  0.169 | 344.2 k | 
| 1M      | LEA25  | 29463-7 | 75.3 kg |  500 k |    15.89 |  1.056 |  62.9 k | 
| 1M      | LEA25  | 29463-7 |  185 kg |  998 k |    27.61 |  0.948 |  36.2 k |
| 1M      | LEA36  | 29463-7 | 13.6 kg |   99 k |     1.11 |  0.012 | 901.9 k | 
| 1M      | LEA36  | 29463-7 | 75.3 kg |  500 k |     3.19 |  0.042 | 313.1 k | 
| 1M      | LEA36  | 29463-7 |  185 kg |  998 k |    10.61 |  0.030 |  94.3 k |
| 1M      | LEA47  | 29463-7 | 13.6 kg |   99 k |     0.60 |  0.018 | 1.664 M | 
| 1M      | LEA47  | 29463-7 | 75.3 kg |  500 k |     1.55 |  0.012 | 646.4 k | 
| 1M      | LEA47  | 29463-7 |  185 kg |  998 k |     2.17 |  0.022 | 460.9 k |
| 1M      | LEA58  | 29463-7 | 13.6 kg |   99 k |     0.57 |  0.012 | 1.754 M |  
| 1M      | LEA58  | 29463-7 | 75.3 kg |  500 k |     0.93 |  0.011 | 1.078 M | 
| 1M      | LEA58  | 29463-7 |  185 kg |  998 k |     1.30 |  0.022 | 772.0 k |

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

## Ten Code Search

In this section, CQL queries for selecting patients which have conditions with one of 10 codes are analyzed. The codes were chosen to produce both a low number and a high number of hits. For the 100k dataset the hits are 395 and 95 k, for the 100k-fh dataset the hits are 2 k and 98 k and for the 1M dataset the hits are 4 k and 954 k.

![](cql/ten-code-search-100k.png)

The first chart shows the results for the 100k dataset. It shows that the performance raises with both the system size and the number of patients found. The performance increase for the query with large number of hits can be explained, because the or-expression does short-cut. That means that the evaluation will end at the first condition found. In the case with a low number of hits, nearly all 10 exists-expressions have to be evaluated, were in the high hit case, a lower amount of exists-expressions have to be evaluated.

![](cql/ten-code-search-100k-fh.png)

The second chart shows the results for the 100k-fh dataset. For the 100k-fh dataset the performance is even better, because with the same codes chosen, the number of hits is higher. This means that having more history for each patient and therefore more conditions available, the same query will run faster.

![](cql/ten-code-search-1M.png)

The third chart shows the results for the 1M dataset. For the two bigger systems LEA47 and LEA58, the relative performance measured in patients per second is identical to the performance Blaze shows at the smaller datasets with only 100 k patients. However the same can't be said for the two smaller systems LEA25 and LEA36, were the relative performance suffers due to memory limitations of that systems.

### Charts Comparing the Datasets

#### System - LEA25

![](cql/ten-code-search-all-datasets-LEA25.png)

For the LEA25 system, the dataset with 1 million patients is to big, because the relative performance suffers compared to both the smaller datasets.

#### System - LEA36

![](cql/ten-code-search-all-datasets-LEA36.png)

With a bit lesser severity, the same can be said for the LEA36 system. It is to small for the 1M dataset.

#### System - LEA47

![](cql/ten-code-search-all-datasets-LEA47.png)

For the LEA47 system, the relative performance is the same for both the 100k and 1M dataset and a bit higher for the 100k-fh dataset due to higher hit numbers.

#### System - LEA58

![](cql/ten-code-search-all-datasets-LEA58.png)

The same can be said for the LEA58 system.

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    395 |     0.13 |  0.006 | 778.5 k |
| 100k    | LEA25  |   95 k |     0.38 |  0.019 | 265.8 k |
| 100k    | LEA36  |    395 |     0.07 |  0.003 | 1.488 M |
| 100k    | LEA36  |   95 k |     0.21 |  0.007 | 471.0 k |
| 100k    | LEA47  |    395 |     0.06 |  0.002 | 1.628 M |
| 100k    | LEA47  |   95 k |     0.13 |  0.004 | 796.7 k |
| 100k    | LEA58  |    395 |     0.06 |  0.002 | 1.705 M |
| 100k    | LEA58  |   95 k |     0.09 |  0.002 | 1.170 M |
| 100k-fh | LEA25  |    2 k |     0.13 |  0.007 | 747.3 k |
| 100k-fh | LEA25  |   98 k |     0.35 |  0.019 | 282.9 k |
| 100k-fh | LEA36  |    2 k |     0.08 |  0.000 | 1.320 M |
| 100k-fh | LEA36  |   98 k |     0.18 |  0.003 | 547.0 k |
| 100k-fh | LEA47  |    2 k |     0.06 |  0.002 | 1.708 M |
| 100k-fh | LEA47  |   98 k |     0.09 |  0.002 | 1.171 M |
| 100k-fh | LEA58  |    2 k |     0.06 |  0.001 | 1.774 M |
| 100k-fh | LEA58  |   98 k |     0.08 |  0.001 | 1.178 M |
| 1M      | LEA25  |    4 k |     1.14 |  0.243 | 873.6 k |
| 1M      | LEA25  |  954 k |    17.57 |  0.530 |  56.9 k |
| 1M      | LEA36  |    4 k |     0.52 |  0.021 | 1.905 M |
| 1M      | LEA36  |  954 k |     5.52 |  0.034 | 181.2 k |
| 1M      | LEA47  |    4 k |     0.50 |  0.004 | 2.002 M |
| 1M      | LEA47  |  954 k |     0.82 |  0.008 | 1.217 M |
| 1M      | LEA58  |    4 k |     0.51 |  0.014 | 1.963 M |
| 1M      | LEA58  |  954 k |     0.73 |  0.008 | 1.378 M |

### CQL Query Frequent

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

### CQL Query Rare

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

## All Code Search

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |   99 k |     0.45 |  0.008 | 220.5 k |
| 100k    | LEA36  |   99 k |     0.24 |  0.008 | 422.0 k |
| 100k    | LEA47  |   99 k |     0.14 |  0.002 | 740.2 k |
| 100k    | LEA58  |   99 k |     0.09 |  0.002 | 1.101 M |
| 100k-fh | LEA25  |  100 k |     0.38 |  0.014 | 263.0 k |
| 100k-fh | LEA36  |  100 k |     0.20 |  0.001 | 493.5 k |
| 100k-fh | LEA47  |  100 k |     0.10 |  0.001 | 1.046 M |
| 100k-fh | LEA58  |  100 k |     0.11 |  0.002 | 893.9 k |
| 1M      | LEA25  |  995 k |    19.89 |  0.855 |  50.3 k |
| 1M      | LEA36  |  995 k |     5.97 |  0.020 | 167.5 k |
| 1M      | LEA47  |  995 k |     1.06 |  0.012 | 947.4 k |
| 1M      | LEA58  |  995 k |     0.74 |  0.003 | 1.344 M |

### CQL Query

```sh
cql/search.sh condition-all
```

## Inpatient Stress Search

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    2 k |     0.69 |  0.027 | 144.9 k |
| 100k    | LEA36  |    2 k |     0.39 |  0.007 | 256.6 k |
| 100k    | LEA47  |    2 k |     0.24 |  0.005 | 422.0 k |
| 100k    | LEA58  |    2 k |     0.16 |  0.002 | 619.2 k |
| 100k-fh | LEA25  |    2 k |     2.18 |  0.036 |  45.9 k |
| 100k-fh | LEA36  |    2 k |     1.40 |  0.014 |  71.2 k |
| 100k-fh | LEA47  |    2 k |     0.51 |  0.003 | 196.6 k |
| 100k-fh | LEA58  |    2 k |     0.53 |  0.003 | 187.9 k |
| 1M      | LEA25  |   16 k |     8.79 |  0.613 | 113.8 k |
| 1M      | LEA36  |   16 k |     3.76 |  0.029 | 265.7 k |
| 1M      | LEA47  |   16 k |     1.82 |  0.009 | 549.2 k |
| 1M      | LEA58  |   16 k |     1.14 |  0.005 | 876.3 k |

### CQL Query

```sh
cql/search.sh inpatient-stress
```

## Condition Code Stratification

### Data

| Dataset | System | # Hits | Time (s) | StdDev | Pat./s |
|---------|--------|-------:|---------:|-------:|-------:|
| 100k    | LEA58  |  5.2 M |    12.79 |  0.325 |  7.8 k |
| 1M      | LEA58  | 52.3 M |   399.64 | 11.966 |  2.5 k |

## Laboratory Observation Code Stratification

### Data

| Dataset | System | # Hits | Time (s) | StdDev | Pat./s |
|---------|--------|-------:|---------:|-------:|-------:|
| 100k    | LEA58  | 37.8 M |   280.40 |  3.026 |      0 |
