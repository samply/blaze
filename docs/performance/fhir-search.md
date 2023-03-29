# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code in **1 second per 1 million found resources** and export the matching resources in **25 seconds per 1 million found resources**, independent of the total number of resources hold.

## Simple Code Search

In this section, FHIR Search for selecting Observation resources with a certain code is used.

### Counting

All measurements are done after Blaze is in a steady state with all resource handles to hit in it's resource handle cache in order to cancel out additional seeks necessary to determine the current version of each resource. A resource handle doesn't contain the actual resource content which is not necessary for counting.

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&_summary=count"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 17861-6 |  171 k |    0.172 |     1.01 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 39156-5 |  967 k |    0.790 |     0.82 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 29463-7 |  1.3 M |    1.232 |     0.95 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 17861-6 |  1.7 M |    1.504 |     0.88 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 39156-5 |  9.7 M |    9.258 |     0.95 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 29463-7 |   13 M |   11.816 |     0.91 |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

According to the measurements the time needed by Blaze to count resources only depends on the number of hits and equals roughly in **1 second per 1 million hits**.

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 17861-6 |  171 k |    4.012 |    23.46 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 39156-5 |  967 k |   23.488 |    24.29 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 29463-7 |  1.3 M |   31.634 |    24.33 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 17861-6 |  1.7 M |   39.058 |    22.98 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 39156-5 |  9.7 M |  223.100 |    23.00 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 29463-7 |   13 M |  309.090 |    23.78 |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

According to the measurements the time needed by Blaze to deliver resources only depends on the number of hits and equals roughly in **25 seconds per 1 million hits**.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_elements=subject&_count=1000" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 17861-6 |  171 k |    2.360 |    13.80 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 39156-5 |  967 k |   13.150 |    13.60 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 29463-7 |  1.3 M |   17.512 |    13.47 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 17861-6 |  1.7 M |   21.562 |    12.68 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 39156-5 |  9.7 M |  131.194 |    13.53 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 29463-7 |   13 M |  182.820 |    14.06 |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

According to the measurements, the time needed by Blaze to deliver subsetted Observations containing only the subject reference is about **twice as fast** as returning the whole resource.

## Code and Value Search

In this section, FHIR Search for selecting Observation resources with a certain code and value is used.

### Counting

All measurements are done after Blaze is in a steady state with all resource handles to hit in it's resource handle cache in order to cancel out additional seeks necessary to determine the current version of each resource. A resource handle doesn't contain the actual resource content which is not necessary for counting.

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_summary=count"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  8.67 |   17 k |      5.1 |      300 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  9.35 |   86 k |      5.1 |       59 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  10.2 |  171 k |      5.1 |       30 |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_count=1000" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  8.67 |   17 k |      5.6 |      329 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  9.35 |   86 k |      7.3 |       85 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  10.2 |  171 k |      9.1 |       53 |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject&_count=1000" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | Value | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|------:|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  8.67 |   17 k |      5.4 |      318 |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  9.35 |   86 k |      6.5 |          |
| EPYC 7543P |     8 GB |        2 GB |     29 M |     28 M | 17861-6 |  10.2 |  171 k |      7.5 |          |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

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

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

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

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

## Used Dataset

The dataset used is Synthea v2.7.0. The resource generation was done with the following settings:

```sh
java -jar synthea-with-dependencies.jar \
  -s 3256262546 -cs 3726451 -r 20210101 -p "$AMOUNT" \
  --exporter.use_uuid_filenames=true \
  --exporter.subfolders_by_id_substring=true \
  --generate.only_alive_patients=true \
  --exporter.hospital.fhir.export=false \
  --exporter.practitioner.fhir.export=false
```

The resulting resources were post-processed with the following jq script:

```
{ resourceType: "Bundle",
  type: "transaction",
  entry: [.entry[]
    # select only resource types we use in queries
    | select(.resource.resourceType == "Patient"
      or .resource.resourceType == "Observation"
      or .resource.resourceType == "Condition")
    # remove the narrative because we don't use it
    | del(.resource.text)
    | del(.resource.encounter)
    ]
}
```

The result is a dataset which consists only of the resource types Patient, Observation and Condition with narratives removed.

## Controlling and Monitoring the Caches

The size of the resource cache and the resource handle cache can be set by their respective environment variables `DB_RESOURCE_CACHE_SIZE` and `DB_RESOURCE_HANDLE_CACHE_SIZE`. The size denotes the number of resources / resource handles. Because one has to specify a number of resources / resource handles, it's important to know how many bytes a resource / resource handle allocates on the heap. For resource handles, it can be said that they allocate between 272 and 328 bytes depending on the size of the resource id. For resources, the size varies widely. Monitoring of the heap usage is critical.

### Monitoring 

Blaze exposes a Prometheus monitoring endpoint on port 8081 per default. The ideal setup would be to attach a Prometheus instance to it and use Grafana as dashboard. But for simple, specific questions about the current state of Blaze, it is sufficient to use `curl` and `grep`.

#### Java Heap Size

The current used bytes of the various generations of the Java heap is provided in the `jvm_memory_pool_bytes_used` metric. Of that generations, the `G1 Old Gen` is the most important, because cached resources will end there. One can use the following command line to fetch all metrics and grep out the right line:

```sh
curl -s http://localhost:8081/metrics | grep jvm_memory_pool_bytes_used | grep Old
jvm_memory_pool_bytes_used{pool="G1 Old Gen",} 8.325004288E9
```

Here the value `8.325004288E9` is in bytes and `E9` means GB. So we have 8.3 GB used old generation here which makes out most of the total heap size. So if you had configured Blaze with a maximum heap size of 10 GB, that usage would be much like a healthy upper limit.

#### Resource / Resource Handle Cache

The resource cache metrics can be found under keys starting with `blaze_db_cache`. Among others there is the `resource-cache` and the `resource-handle-cache`. The metrics are a bit more difficult to interpret without a Prometheus/Grafana infrastructure, because they are counters starting Blaze startup. So after a longer runtime, one has to calculate relative differences here. But right after the start of Blaze, the numbers are very useful on its own. 

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
