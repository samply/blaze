# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code in **0.5 seconds per 1 million found resources** and export the matching resources in **20 seconds per 1 million found resources**, independent of the total number of resources hold.

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache ¹ |
|--------|----------|------------|------:|--------:|-------:|---------:|------------:|-----------------:|
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB |   2 TB |   32 GiB |      32 GiB |             10 M | 
| CCX42  | Hetzner  | EPYC 7763  |    16 |  64 GiB | 360 GB |   16 GiB |       8 GiB |              5 M | 

¹ Size of the resource cache (DB_RESOURCE_CACHE_SIZE)

## Datasets

The following datasets were used:

| Dataset | # Pat. ¹ | # Res. ² | # Obs. ³ |
|---------|---------:|---------:|---------:|
| 100k    |    100 k |    104 M |     59 M |
| 1M      |      1 M |   1044 M |    593 M |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

## Simple Code Search

In this section, FHIR Search for selecting Observation resources with a certain code is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| LEA47  | 100k    | 8310-5  |  115 k |     0.08 |  0.005 |   0.66 |
| LEA47  | 100k    | 55758-7 |  1.0 M |     0.56 |  0.017 |   0.55 |
| LEA47  | 100k    | 72514-3 |  2.7 M |     1.63 |  0.017 |   0.59 |
| CCX42  | 100k    | 8310-5  |  115 k |     0.07 |  0.005 |   0.62 |
| CCX42  | 100k    | 55758-7 |  1.0 M |     0.53 |  0.062 |   0.52 |
| CCX42  | 100k    | 72514-3 |  2.7 M |     1.31 |  0.017 |   0.47 |
| LEA47  | 1M      | 8310-5  |  1.1 M |     0.67 |  0.011 |   0.57 |
| LEA47  | 1M      | 55758-7 | 10.1 M |     6.08 |  0.066 |   0.59 |
| LEA47  | 1M      | 72514-3 | 27.3 M |    16.37 |  0.234 |   0.59 |

¹ time in seconds per 1 million resources

According to the measurements the time needed by Blaze to count resources only depends on the number of hits and equals roughly in **0.5 seconds per 1 million hits**.

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) |  StdDev |  T/1M ¹ |
|--------|---------|---------|-------:|---------:|--------:|--------:|
| LEA47  | 100k    | 8310-5  |  115 k |     2.06 |   0.014 |   17.88 |  
| LEA47  | 100k    | 55758-7 |  1.0 M |    17.34 |   0.175 |   17.21 |
| LEA47  | 100k    | 72514-3 |  2.7 M |    45.62 |   0.464 |   16.60 |
| CCX42  | 100k    | 8310-5  |  115 k |     2.46 |   0.044 |   21.34 |           
| CCX42  | 100k    | 55758-7 |  1.0 M |    19.74 |   0.237 |   19.60 |         
| CCX42  | 100k    | 72514-3 |  2.7 M |    52.95 |   0.484 |   19.26 |         
| LEA47  | 1M      | 8310-5  |  1.1 M |    21.51 |   0.192 |   18.55 |         
| LEA47  | 1M      | 55758-7 | 10.1 M |   233.01 |   2.150 |   22.98 |         
| LEA47  | 1M      | 72514-3 | 27.3 M |   966.59 | 150.132 | 35.34 ² |

¹ time in seconds per 1 million resources, ² resource cache size (10 million) is smaller than the number of resources returned (27.3 million)

According to the measurements the time needed by Blaze to deliver resources only depends on the number of hits and equals roughly in **20 seconds per 1 million hits**.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | 8310-5  |  115 k |     1.42 |  0.043 |   12.32 |
| LEA47  | 100k    | 55758-7 |  1.0 M |    10.96 |  0.097 |   10.87 |
| LEA47  | 100k    | 72514-3 |  2.7 M |    27.62 |  0.277 |   10.05 |
| CCX42  | 100k    | 8310-5  |  115 k |     1.78 |  0.052 |   15.41 |           
| CCX42  | 100k    | 55758-7 |  1.0 M |    14.46 |  0.177 |   14.35 |           
| CCX42  | 100k    | 72514-3 |  2.7 M |    37.82 |  0.107 |   13.76 |
| LEA47  | 1M      | 8310-5  |  1.1 M |    15.21 |  0.161 |   13.11 |          
| LEA47  | 1M      | 55758-7 | 10.1 M |   167.34 |  0.942 |   16.50 |          
| LEA47  | 1M      | 72514-3 | 27.3 M |   662.15 |  8.179 | 24.21 ² |

¹ time in seconds per 1 million resources, ² resource cache size (10 million) is smaller than the number of resources returned (27.3 million)

According to the measurements, the time needed by Blaze to deliver subsetted Observations containing only the subject reference only depends on the number of hits and equals roughly in **15 seconds per 1 million hits**.

## Code and Value Search

In this section, FHIR Search for selecting Observation resources with a certain code and value is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_summary=count"
```

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|-------:|
| LEA47  | 100k    | 29463-7 |  26.8 |  158 k |    14.24 |  0.073 |  90.05 |
| LEA47  | 100k    | 29463-7 |  79.5 |  790 k |    14.68 |  0.152 |  18.59 |
| LEA47  | 100k    | 29463-7 |   183 |  1.6 M |    14.61 |  0.120 |   9.23 |
| CCX42  | 100k    | 29463-7 |  26.8 |  158 k |    56.45 |  0.149 | 357.08 |
| CCX42  | 100k    | 29463-7 |  79.5 |  790 k |    56.72 |  0.174 |  71.84 |
| CCX42  | 100k    | 29463-7 |   183 |  1.6 M |    56.77 |  0.135 |  35.87 |
| LEA47  | 1M      | 29463-7 |  26.8 |
| LEA47  | 1M      | 29463-7 |  79.5 |
| LEA47  | 1M      | 29463-7 |   183 |

¹ time in seconds per 1 million resources

The measurements show that the time Blaze needs to count resources with two search params (code and value-quantity) is constant. In fact it depends only on the number of resources which qualify for the first search parameter which can be seen on the fixed time of 56 seconds.

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_count=1000" > /dev/null"
```

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|-------:|
| LEA47  | 100k    | 29463-7 |  26.8 |  158 k |    16.77 |  0.045 | 106.06 |
| LEA47  | 100k    | 29463-7 |  79.5 |  790 k |    20.84 |  0.193 |  26.39 |
| LEA47  | 100k    | 29463-7 |   183 |  1.6 M |    35.42 |  0.298 |  22.38 |
| CCX42  | 100k    | 29463-7 |  26.8 |  158 k |    59.19 |  0.060 | 374.44 |
| CCX42  | 100k    | 29463-7 |  79.5 |  790 k |    70.26 |  0.142 |  88.98 |
| CCX42  | 100k    | 29463-7 |   183 |  1.6 M |    83.82 |  0.076 |  52.97 |
| LEA47  | 1M      | 29463-7 |  26.8 |
| LEA47  | 1M      | 29463-7 |  79.5 |
| LEA47  | 1M      | 29463-7 |   183 |

¹ time in seconds per 1 million resources

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|-------:|
| LEA47  | 100k    | 29463-7 |  26.8 |  158 k |    15.82 |  0.045 | 100.09 |
| LEA47  | 100k    | 29463-7 |  79.5 |  790 k |    25.50 |  0.148 |  32.29 |
| LEA47  | 100k    | 29463-7 |   183 |  1.6 M |    26.93 |  0.132 |  17.02 |
| CCX42  | 100k    | 29463-7 |  26.8 |  158 k |    58.07 |  0.028 | 367.36 |
| CCX42  | 100k    | 29463-7 |  79.5 |  790 k |    65.31 |  0.197 |  82.71 |
| CCX42  | 100k    | 29463-7 |   183 |  1.6 M |    74.40 |  0.183 |  47.01 |
| LEA47  | 1M      | 29463-7 |  26.8 |
| LEA47  | 1M      | 29463-7 |  79.5 |
| LEA47  | 1M      | 29463-7 |   183 |

¹ time in seconds per 1 million resources

### Download of Resources using the Combined Search Param

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code-value-quantity=http://loinc.org|$CODE\$lt$VALUE|http://unitsofmeasure.org|$UNIT&_count=1000" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  8.67 |   17 k |      0.4 |       24 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  9.35 |   86 k |      2.0 |       23 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  10.2 |  171 k |      4.2 |       25 |

¹ Total Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, EPYC 7543P - The system has 16 cores and 128 GB RAM, H. CCX42 - The system has 16 cores and 64 GB RAM.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject&_count=1000" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  8.67 |   17 k |      0.2 |          |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  9.35 |   86 k |      1.3 |          |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  10.2 |  171 k |      2.4 |          |

¹ Total Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, EPYC 7543P - The system has 16 cores and 128 GB RAM, H. CCX42 - The system has 16 cores and 64 GB RAM.


## Simple Date Search

In this section, FHIR Search for selecting Observation resources with a certain effective year is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?date=$YEAR&_summary=count"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|------|-------:|---------:|-------:|-------:|
| LEA47  | 100k    | 2013 |  3.1 M |     2.56 |  0.024 |   0.81 |
| LEA47  | 100k    | 2019 |  6.0 M |     4.82 |  0.138 |   0.80 |
| CCX42  | 100k    | 2013 |  3.1 M |     2.00 |  0.028 |   0.63 |
| CCX42  | 100k    | 2019 |  6.0 M |     3.91 |  0.142 |   0.65 |
| LEA47  | 1M      | 2013 | 31.1 M |    23.31 |  0.206 |   0.75 |
| LEA47  | 1M      | 2019 | 60.0 M |    45.98 |  0.582 |   0.76 |

¹ time in seconds per 1 million resources

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_count=1000" > /dev/null"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | 2013 |  3.1 M |    56.35 |  1.001 |   18.02 |
| LEA47  | 100k    | 2019 |  6.0 M |   103.39 |  0.877 |   17.29 |
| CCX42  | 100k    | 2013 |  3.1 M |   128.16 |  0.406 |   41.00 |
| CCX42  | 100k    | 2019 |  6.0 M |   276.13 |  2.020 | 46.18 ² |
| LEA47  | 1M      | 2013 | 31.1 M |   991.28 | 12.329 | 31.90 ² |
| LEA47  | 1M      | 2019 | 60.0 M |  2083.44 | 31.983 | 34.69 ² |

¹ time in seconds per 1 million resources, ² resource cache size is smaller than the number of resources returned

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA47  | 100k    | 2013 |  3.1 M |    35.15 |  0.688 |   11.24 |
| LEA47  | 100k    | 2019 |  6.0 M |    66.72 |  0.521 |   11.16 |
| CCX42  | 100k    | 2013 |  3.1 M |    85.59 |  0.293 |   27.38 |
| CCX42  | 100k    | 2019 |  6.0 M |   179.29 |  1.786 | 29.99 ² |
| LEA47  | 1M      | 2013 | 31.1 M |   673.36 | 10.199 | 21.67 ² |
| LEA47  | 1M      | 2019 | 60.0 M |  1516.90 |  0.482 | 25.25 ² |

¹ time in seconds per 1 million resources, ² resource cache size is smaller than the number of resources returned

## Used Dataset

The dataset used is generated with Synthea v3.1.1. The resource generation is described [here](synthea/README.md).

## Controlling and Monitoring the Caches

The size of the resource cache can be set by its respective environment variable `DB_RESOURCE_CACHE_SIZE`. The size denotes the number of resources. Because one has to specify a number of resources, it's important to know how many bytes a resource allocates on the heap. The size varies widely. Monitoring of the heap usage is critical.

### Monitoring 

Blaze exposes a Prometheus monitoring endpoint on port 8081 per default. The ideal setup would be to attach a Prometheus instance to it and use Grafana as dashboard. But for simple, specific questions about the current state of Blaze, it is sufficient to use `curl` and `grep`.

#### Java Heap Size

The current used bytes of the various generations of the Java heap is provided in the `jvm_memory_pool_bytes_used` metric. Of that generations, the `G1 Old Gen` is the most important, because cached resources will end there. One can use the following command line to fetch all metrics and grep out the right line:

```sh
curl -s http://localhost:8081/metrics | grep jvm_memory_pool_bytes_used | grep Old
jvm_memory_pool_bytes_used{pool="G1 Old Gen",} 8.325004288E9
```

Here the value `8.325004288E9` is in bytes and `E9` means GB. So we have 8.3 GB used old generation here which makes out most of the total heap size. So if you had configured Blaze with a maximum heap size of 10 GB, that usage would be much like a healthy upper limit.

#### Resource Cache

The resource cache metrics can be found under keys starting with `blaze_db_cache`. Among others there is the `resource-cache`. The metrics are a bit more difficult to interpret without a Prometheus/Grafana infrastructure, because they are counters starting Blaze startup. So after a longer runtime, one has to calculate relative differences here. But right after the start of Blaze, the numbers are very useful on its own. 

```sh
curl -s http://localhost:8081/metrics | grep blaze_db_cache | grep resource-cache
blaze_db_cache_hits_total{name="resource-cache",} 869000.0
blaze_db_cache_loads_total{name="resource-cache",} 13214.0
blaze_db_cache_load_failures_total{name="resource-cache",} 0.0
blaze_db_cache_load_seconds_total{name="resource-cache",} 234.418864426
blaze_db_cache_evictions_total{name="resource-cache",} 0.0
```

Here the important part would be the number of evictions. As long as the number of evictions is still zero, the resource cache did not overflow already. It should be the goal that most CQL queries or FHIR Search queries with export should fit into the resource cache. Otherwise, if the number of resources of a single query do not fit in the resource cache, the cache has to be evicted and filled up during that single query. Especially if you repeat the query, the resources needed at the start of the query will be no longer in the cache and after they are loaded, the resources one needs at the end of the query will be also not in the cache. So having a cache size smaller as needed to run a single query doesn't give any performance benefit. 

[1]: <https://www.hl7.org/fhir/search.html#elements>
