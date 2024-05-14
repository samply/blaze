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
| 100k    | LEA25  | 17861-6 |    2 k |     0.16 |  0.011 | 630.6 k | 
| 100k    | LEA25  | 8310-5  |   60 k |     0.25 |  0.017 | 393.1 k | 
| 100k    | LEA25  | 72514-3 |  100 k |     0.29 |  0.009 | 341.5 k |
| 100k    | LEA36  | 17861-6 |    2 k |     0.09 |  0.006 | 1.086 M | 
| 100k    | LEA36  | 8310-5  |   60 k |     0.12 |  0.003 | 807.1 k | 
| 100k    | LEA36  | 72514-3 |  100 k |     0.17 |  0.006 | 604.8 k |
| 100k    | LEA47  | 17861-6 |    2 k |     0.06 |  0.002 | 1.629 M | 
| 100k    | LEA47  | 8310-5  |   60 k |     0.08 |  0.004 | 1.307 M | 
| 100k    | LEA47  | 72514-3 |  100 k |     0.09 |  0.003 | 1.068 M |
| 100k    | LEA58  | 17861-6 |    2 k |     0.07 |  0.002 | 1.504 M | 
| 100k    | LEA58  | 8310-5  |   60 k |     0.08 |  0.001 | 1.298 M | 
| 100k    | LEA58  | 72514-3 |  100 k |     0.08 |  0.002 | 1.207 M |
| 100k-fh | LEA25  | 788-0   |    2 k |     0.21 |  0.009 | 475.3 k |
| 100k-fh | LEA25  | 44261-6 |   57 k |     0.30 |  0.012 | 331.4 k |
| 100k-fh | LEA25  | 72514-3 |  100 k |     0.38 |  0.021 | 265.8 k |
| 100k-fh | LEA36  | 788-0   |    2 k |     0.12 |  0.007 | 860.0 k |
| 100k-fh | LEA36  | 44261-6 |   57 k |     0.17 |  0.008 | 573.5 k |
| 100k-fh | LEA36  | 72514-3 |  100 k |     0.20 |  0.007 | 490.9 k |
| 100k-fh | LEA47  | 788-0   |    2 k |     0.07 |  0.002 | 1.415 M |
| 100k-fh | LEA47  | 44261-6 |   57 k |     0.10 |  0.002 | 995.8 k |
| 100k-fh | LEA47  | 72514-3 |  100 k |     0.12 |  0.004 | 809.9 k |
| 100k-fh | LEA58  | 788-0   |    2 k |     0.06 |  0.003 | 1.659 M |
| 100k-fh | LEA58  | 44261-6 |   57 k |     0.07 |  0.002 | 1.521 M |
| 100k-fh | LEA58  | 72514-3 |  100 k |     0.08 |  0.001 | 1.232 M |
| 1M      | LEA25  | 17861-6 |   25 k |     8.04 |  0.059 | 124.3 k | 
| 1M      | LEA25  | 8310-5  |  603 k |    11.40 |  0.043 |  87.7 k | 
| 1M      | LEA25  | 72514-3 |  998 k |    13.16 |  0.049 |  76.0 k |
| 1M      | LEA36  | 17861-6 |   25 k |     3.90 |  0.009 | 256.1 k | 
| 1M      | LEA36  | 8310-5  |  603 k |     5.74 |  0.023 | 174.2 k | 
| 1M      | LEA36  | 72514-3 |  998 k |     6.68 |  0.036 | 149.6 k |
| 1M      | LEA47  | 17861-6 |   25 k |     0.59 |  0.003 | 1.705 M | 
| 1M      | LEA47  | 8310-5  |  603 k |     0.64 |  0.003 | 1.557 M | 
| 1M      | LEA47  | 72514-3 |  998 k |     0.76 |  0.006 | 1.324 M |
| 1M      | LEA58  | 17861-6 |   25 k |     0.61 |  0.005 | 1.633 M |
| 1M      | LEA58  | 8310-5  |  603 k |     0.67 |  0.005 | 1.495 M | 
| 1M      | LEA58  | 72514-3 |  998 k |     0.75 |  0.003 | 1.336 M |

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
| 100k    | LEA25  | 29463-7 | 13.6 kg |   10 k |     2.64 |  0.033 |  37.9 k | 
| 100k    | LEA25  | 29463-7 | 75.3 kg |   50 k |     1.41 |  0.007 |  70.7 k | 
| 100k    | LEA25  | 29463-7 |  185 kg |  100 k |     0.35 |  0.018 | 285.1 k |
| 100k    | LEA36  | 29463-7 | 13.6 kg |   10 k |     1.04 |  0.012 |  95.9 k | 
| 100k    | LEA36  | 29463-7 | 75.3 kg |   50 k |     0.71 |  0.006 | 141.3 k | 
| 100k    | LEA36  | 29463-7 |  185 kg |  100 k |     0.20 |  0.009 | 511.1 k |
| 100k    | LEA47  | 29463-7 | 13.6 kg |   10 k |     0.68 |  0.004 | 147.2 k | 
| 100k    | LEA47  | 29463-7 | 75.3 kg |   50 k |     0.47 |  0.010 | 214.1 k | 
| 100k    | LEA47  | 29463-7 |  185 kg |  100 k |     0.12 |  0.004 | 855.7 k |
| 100k    | LEA58  | 29463-7 | 13.6 kg |   10 k |     0.47 |  0.004 | 210.7 k |  
| 100k    | LEA58  | 29463-7 | 75.3 kg |   50 k |     0.31 |  0.002 | 320.2 k | 
| 100k    | LEA58  | 29463-7 |  185 kg |  100 k |     0.09 |  0.001 | 1.057 M |
| 100k-fh | LEA25  | 29463-7 | 13.6 kg |  100 k |     1.40 |  0.046 |  71.3 k |  
| 100k-fh | LEA25  | 29463-7 | 75.3 kg |  100 k |     0.86 |  0.007 | 116.4 k | 
| 100k-fh | LEA25  | 29463-7 |  185 kg |  100 k |     0.44 |  0.018 | 228.2 k |
| 100k-fh | LEA36  | 29463-7 | 13.6 kg |  100 k |     0.78 |  0.003 | 127.9 k |  
| 100k-fh | LEA36  | 29463-7 | 75.3 kg |  100 k |     0.49 |  0.015 | 202.8 k | 
| 100k-fh | LEA36  | 29463-7 |  185 kg |  100 k |     0.25 |  0.006 | 397.9 k |
| 100k-fh | LEA47  | 29463-7 | 13.6 kg |  100 k |     0.45 |  0.005 | 222.7 k |  
| 100k-fh | LEA47  | 29463-7 | 75.3 kg |  100 k |     0.29 |  0.005 | 349.2 k | 
| 100k-fh | LEA47  | 29463-7 |  185 kg |  100 k |     0.15 |  0.005 | 663.4 k |
| 100k-fh | LEA58  | 29463-7 | 13.6 kg |  100 k |     0.30 |  0.002 | 331.9 k |  
| 100k-fh | LEA58  | 29463-7 | 75.3 kg |  100 k |     0.19 |  0.002 | 536.2 k | 
| 100k-fh | LEA58  | 29463-7 |  185 kg |  100 k |     0.10 |  0.004 | 976.5 k |
| 1M      | LEA25  | 29463-7 | 13.6 kg |   99 k |   719.84 |  3.734 |   1.4 k | 
| 1M      | LEA25  | 29463-7 | 75.3 kg |  500 k |   479.52 | 11.096 |   2.1 k | 
| 1M      | LEA25  | 29463-7 |  185 kg |  998 k |   103.51 | 40.442 |   9.7 k |
| 1M      | LEA36  | 29463-7 | 13.6 kg |   99 k |   432.80 |  1.586 |   2.3 k | 
| 1M      | LEA36  | 29463-7 | 75.3 kg |  500 k |   265.29 |  1.618 |   3.8 k | 
| 1M      | LEA36  | 29463-7 |  185 kg |  998 k |     7.72 |  0.045 | 129.5 k |
| 1M      | LEA47  | 29463-7 | 13.6 kg |   99 k |   138.82 |  1.378 |   7.2 k | 
| 1M      | LEA47  | 29463-7 | 75.3 kg |  500 k |     8.09 |  0.015 | 123.6 k | 
| 1M      | LEA47  | 29463-7 |  185 kg |  998 k |     1.03 |  0.004 | 973.7 k |
| 1M      | LEA58  | 29463-7 | 13.6 kg |   99 k |     4.18 |  0.004 | 239.3 k |  
| 1M      | LEA58  | 29463-7 | 75.3 kg |  500 k |     2.67 |  0.008 | 374.2 k | 
| 1M      | LEA58  | 29463-7 |  185 kg |  998 k |     0.78 |  0.003 | 1.288 M |

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
| 100k    | LEA25  |    395 |     0.70 |  0.011 | 142.0 k |
| 100k    | LEA25  |   95 k |     0.44 |  0.022 | 227.6 k |
| 100k    | LEA36  |    395 |     0.40 |  0.010 | 249.3 k |
| 100k    | LEA36  |   95 k |     0.22 |  0.009 | 448.5 k |
| 100k    | LEA47  |    395 |     0.23 |  0.001 | 437.5 k |
| 100k    | LEA47  |   95 k |     0.14 |  0.002 | 731.9 k |
| 100k    | LEA58  |    395 |     0.16 |  0.001 | 607.1 k |
| 100k    | LEA58  |   95 k |     0.11 |  0.002 | 941.9 k |
| 100k-fh | LEA25  |    2 k |     1.30 |  0.008 |  76.7 k |
| 100k-fh | LEA25  |   98 k |     0.47 |  0.006 | 214.4 k |
| 100k-fh | LEA36  |    2 k |     0.75 |  0.008 | 133.1 k |
| 100k-fh | LEA36  |   98 k |     0.29 |  0.009 | 343.9 k |
| 100k-fh | LEA47  |    2 k |     0.45 |  0.003 | 224.7 k |
| 100k-fh | LEA47  |   98 k |     0.16 |  0.003 | 628.0 k |
| 100k-fh | LEA58  |    2 k |     0.31 |  0.003 | 322.3 k |
| 100k-fh | LEA58  |   98 k |     0.10 |  0.002 |   1.0 M |
| 1M      | LEA25  |    4 k |    13.60 |  0.073 |  73.5 k |
| 1M      | LEA25  |  954 k |    11.87 |  0.027 |  84.2 k |
| 1M      | LEA36  |    4 k |     7.24 |  0.009 | 138.1 k |
| 1M      | LEA36  |  954 k |     6.09 |  0.035 | 164.3 k |
| 1M      | LEA47  |    4 k |     2.11 |  0.005 | 473.3 k |
| 1M      | LEA47  |  954 k |     1.18 |  0.003 | 846.4 k |
| 1M      | LEA58  |    4 k |     1.39 |  0.003 | 719.2 k |
| 1M      | LEA58  |  954 k |     0.95 |  0.004 | 1.053 M |

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

## Condition Code Stratification

### Data

| Dataset | System | # Hits | Time (s) | StdDev | Pat./s |
|---------|--------|-------:|---------:|-------:|-------:|
| 1M      | LEA58  | 52.3 M |   399.64 | 11.966 |  2.5 k |

## Laboratory Observation Code Stratification

### Data

| Dataset | System | # Hits | Time (s) | StdDev | Pat./s |
|---------|--------|-------:|---------:|-------:|-------:|
| 100k    | LEA58  | 37.8 M |   280.40 |  3.026 |      0 |
