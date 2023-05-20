# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code in **0.5 seconds per 1 million found resources** and export the matching resources in **20 seconds per 1 million found resources**, independent of the total number of resources hold.

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache ¹ |
|--------|----------|------------|------:|--------:|-------:|---------:|------------:|-----------------:|
| LE1080 | on-prem  | EPYC 7543P |    16 | 128 GiB |   2 TB |   32 GiB |      16 GiB |             11 M | 
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

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ | Remarks    |
|--------|---------|---------|-------:|---------:|-------:|-------:|------------|
| LE1080 | 100k    | 8310-5  |  115 k |     0.07 |  0.003 |   0.59 | Overhead ² |
| LE1080 | 100k    | 55758-7 |  1.0 M |     0.50 |  0.008 |   0.50 |            |
| LE1080 | 100k    | 72514-3 |  2.7 M |     1.36 |  0.016 |   0.49 |            |
| CCX42  | 100k    | 8310-5  |  115 k |     0.07 |  0.005 |   0.62 | Overhead ² |
| CCX42  | 100k    | 55758-7 |  1.0 M |     0.53 |  0.062 |   0.52 |            |
| CCX42  | 100k    | 72514-3 |  2.7 M |     1.31 |  0.017 |   0.47 |            |
| LE1080 | 1M      | 8310-5  |  1.1 M |     0.60 |  0.004 |   0.51 |            |
| LE1080 | 1M      | 55758-7 | 10.1 M |     5.59 |  0.020 |   0.55 |            |
| LE1080 | 1M      | 72514-3 | 27.3 M |    14.55 |  0.039 |   0.53 |            |

¹ time in seconds per 1 million resources, ² Because the measurement of the duration includes the whole HTTP stack, durations of small results like the 115 k includes an overhead that results in a duration more like 0.6 seconds per 1 million hits.

According to the measurements the time needed by Blaze to count resources only depends on the number of hits and equals roughly in **0.5 seconds per 1 million hits**.

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ | Remarks |
|--------|---------|---------|-------:|---------:|-------:|-------:|---------|
| LE1080 | 100k    | 8310-5  |  115 k |     2.29 |  0.008 |  19.84 |  
| LE1080 | 100k    | 55758-7 |  1.0 M |    18.29 |  0.107 |  18.15 |
| LE1080 | 100k    | 72514-3 |  2.7 M |    50.09 |  0.549 |  18.22 |
| CCX42  | 100k    | 8310-5  |  115 k |     2.46 |  0.044 |  21.34 |           
| CCX42  | 100k    | 55758-7 |  1.0 M |    19.74 |  0.237 |  19.60 |         
| CCX42  | 100k    | 72514-3 |  2.7 M |    52.95 |  0.484 |  19.26 |         
| LE1080 | 1M      | 8310-5  |  1.1 M |    26.17 |  0.186 |  22.57 |         
| LE1080 | 1M      | 55758-7 | 10.1 M |   237.82 |  1.534 |  23.45 |         
| LE1080 | 1M      | 72514-3 | 27.3 M |  1038.65 | 61.478 |  37.98 | Cache ² |

¹ time in seconds per 1 million resources, ² resource cache size (11 million) is smaller than the number of resources returned (27.3 million)

According to the measurements the time needed by Blaze to deliver resources only depends on the number of hits and equals roughly in **20 seconds per 1 million hits**.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ | Remarks |
|--------|---------|---------|-------:|---------:|-------:|-------:|---------|         
| LE1080 | 100k    | 8310-5  |  115 k |     1.74 |  0.035 |  15.14 |
| LE1080 | 100k    | 55758-7 |  1.0 M |    12.92 |  0.080 |  12.82 |
| LE1080 | 100k    | 72514-3 |  2.7 M |    34.31 |  0.294 |  12.48 |
| CCX42  | 100k    | 8310-5  |  115 k |     1.78 |  0.052 |  15.41 |           
| CCX42  | 100k    | 55758-7 |  1.0 M |    14.46 |  0.177 |  14.35 |           
| CCX42  | 100k    | 72514-3 |  2.7 M |    37.82 |  0.107 |  13.76 |
| LE1080 | 1M      | 8310-5  |  1.1 M |    19.88 |  0.112 |  17.15 |          
| LE1080 | 1M      | 55758-7 | 10.1 M |   178.90 |  0.786 |  17.64 |          
| LE1080 | 1M      | 72514-3 | 27.3 M |   808.84 | 55.742 |  29.58 | Cache ² |

¹ time in seconds per 1 million resources, ² resource cache size (11 million) is smaller than the number of resources returned (27.3 million)

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
| LE1080 | 100k    | 29463-7 |  26.8 |
| LE1080 | 100k    | 29463-7 |  79.5 |
| LE1080 | 100k    | 29463-7 |   183 |
| CCX42  | 100k    | 29463-7 |  26.8 |  158 k |    56.45 |  0.149 | 357.08 |
| CCX42  | 100k    | 29463-7 |  79.5 |  790 k |    56.72 |  0.174 |  71.84 |
| CCX42  | 100k    | 29463-7 |   183 |  1.6 M |    56.77 |  0.135 |  35.87 |
| LE1080 | 1M      | 29463-7 |  26.8 |
| LE1080 | 1M      | 29463-7 |  79.5 |
| LE1080 | 1M      | 29463-7 |   183 |

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
| LE1080 | 100k    | 29463-7 |  26.8 |
| LE1080 | 100k    | 29463-7 |  79.5 |
| LE1080 | 100k    | 29463-7 |   183 |
| CCX42  | 100k    | 29463-7 |  26.8 |  158 k |    59.19 |  0.060 | 374.44 |
| CCX42  | 100k    | 29463-7 |  79.5 |  790 k |    70.26 |  0.142 |  88.98 |
| CCX42  | 100k    | 29463-7 |   183 |  1.6 M |    83.82 |  0.076 |  52.97 |
| LE1080 | 1M      | 29463-7 |  26.8 |
| LE1080 | 1M      | 29463-7 |  79.5 |
| LE1080 | 1M      | 29463-7 |   183 |

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
| LE1080 | 100k    | 29463-7 |  26.8 |
| LE1080 | 100k    | 29463-7 |  79.5 |
| LE1080 | 100k    | 29463-7 |   183 |
| CCX42  | 100k    | 29463-7 |  26.8 |  158 k |    58.07 |  0.028 | 367.36 |
| CCX42  | 100k    | 29463-7 |  79.5 |  790 k |    65.31 |  0.197 |  82.71 |
| CCX42  | 100k    | 29463-7 |   183 |  1.6 M |    74.40 |  0.183 |  47.01 |
| LE1080 | 1M      | 29463-7 |  26.8 |
| LE1080 | 1M      | 29463-7 |  79.5 |
| LE1080 | 1M      | 29463-7 |   183 |

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
| LE1080 | 100k    | 2013 |  3.1 M |     2.32 |  0.044 |   0.74 |
| LE1080 | 100k    | 2019 |  6.0 M |     4.31 |  0.087 |   0.72 |
| CCX42  | 100k    | 2013 |  3.1 M |     2.00 |  0.028 |   0.63 |
| CCX42  | 100k    | 2019 |  6.0 M |     3.91 |  0.142 |   0.65 |
| LE1080 | 1M      | 2013 | 31.1 M |    22.69 |  0.426 |   0.73 |
| LE1080 | 1M      | 2019 | 60.0 M |    45.18 |  0.574 |   0.75 |

¹ time in seconds per 1 million resources

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_count=1000" > /dev/null"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev | T/1M ¹ | Remarks |
|--------|---------|------|-------:|---------:|-------:|-------:|---------|
| LE1080 | 100k    | 2013 |  3.1 M |    63.11 |  0.564 |  20.19 |         |
| LE1080 | 100k    | 2019 |  6.0 M |   124.12 |  2.017 |  20.76 |         |
| CCX42  | 100k    | 2013 |  3.1 M |   128.16 |  0.406 |  41.00 |         |
| CCX42  | 100k    | 2019 |  6.0 M |   276.13 |  2.020 |  46.18 | Cache ² |
| LE1080 | 1M      | 2013 |
| LE1080 | 1M      | 2019 | 60.0 M |  3285.64 |  3.663 |  54.71 | Cache ² |

¹ time in seconds per 1 million resources, ² resource cache size (5 million) is smaller than the number of resources returned (6 million)

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev | T/1M ¹ | Remarks |
|--------|---------|------|-------:|---------:|-------:|-------:|---------|
| LE1080 | 100k    | 2013 |  3.1 M |    41.90 |  0.150 |  13.40 |         |
| LE1080 | 100k    | 2019 |  6.0 M |    81.32 |  0.454 |  13.60 |         |
| CCX42  | 100k    | 2013 |  3.1 M |    85.59 |  0.293 |  27.38 |         |
| CCX42  | 100k    | 2019 |  6.0 M |   179.29 |  1.786 |  29.99 | Cache ² |
| LE1080 | 1M      | 2013 |
| LE1080 | 1M      | 2019 | 60.0 M |  2720.06 |  7.280 |  45.29 | Cache ² |

¹ time in seconds per 1 million resources, ² resource cache size (5 million) is smaller than the number of resources returned (6 million)

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
