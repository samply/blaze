# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code in **1 second per 1 million found resources** and export the matching resources in **30 seconds per 1 million found resources**, independent of the total number of resources hold.

## Simple Code Search

In this section, FHIR Search for selecting Observation resources with a certain code is used.

### Counting

All measurements are done after Blaze is in a steady state with all resource handles to hit in it's resource handle cache in order to cancel out additional seeks necessary to determine the current version of each resource. A resource handle doesn't contain the actual resource content which is not necessary for counting.

Counting is done using the following `curl` command:

```sh
time curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&_summary=count"
```

| CPU         | RAM (GB) | Heap Mem (GB) | Block Cache (GB) | # Resources | # Observations | Code    | # Hits | Time (s) |
|-------------|---------:|--------------:|-----------------:|------------:|---------------:|---------|-------:|---------:|
| E5-2687W v4 |      128 |             4 |                1 |        29 M |           28 M | 17861-6 |  171 k |      0.2 |
| E5-2687W v4 |      128 |             4 |                1 |        29 M |           28 M | 39156-5 |  967 k |        1 |
| E5-2687W v4 |      128 |             4 |                1 |        29 M |           28 M | 29463-7 |  1.3 M |      1.6 |
| E5-2687W v4 |      128 |            30 |               10 |       292 M |          278 M | 17861-6 |  1.7 M |      1.7 |
| E5-2687W v4 |      128 |            30 |               10 |       292 M |          278 M | 39156-5 |  9.7 M |       10 |
| E5-2687W v4 |      128 |            30 |               10 |       292 M |          278 M | 29463-7 |   13 M |       15 |
| EPYC 7543P  |      128 |             4 |                1 |        29 M |           28 M | 17861-6 |  171 k |    0.152 |
| EPYC 7543P  |      128 |             4 |                1 |        29 M |           28 M | 39156-5 |  967 k |    0.804 |
| EPYC 7543P  |      128 |             4 |                1 |        29 M |           28 M | 29463-7 |  1.3 M |    1.088 |
| EPYC 7543P  |      128 |            30 |               10 |       292 M |          278 M | 17861-6 |  1.7 M |    1.304 |
| EPYC 7543P  |      128 |            30 |               10 |       292 M |          278 M | 39156-5 |  9.7 M |    7.768 |
| EPYC 7543P  |      128 |            30 |               10 |       292 M |          278 M | 29463-7 |   13 M |    9.954 |

According to the measurements the time needed by Blaze to count resources only depends on the number of hits and equals roughly in **1 second per 1 million hits**.

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" -o "$CODE.ndjson"
```

| CPU         | RAM (GB) | Heap Mem (GB) | Block Cache (GB) | # Resources | # Observations | Code    | # Hits | Time (s) |
|-------------|---------:|--------------:|-----------------:|------------:|---------------:|---------|-------:|---------:|
| E5-2687W v4 |      128 |             4 |                1 |        29 M |           28 M | 17861-6 |  171 k |      4.6 |
| E5-2687W v4 |      128 |             4 |                1 |        29 M |           28 M | 39156-5 |  967 k |       26 |
| E5-2687W v4 |      128 |             4 |                1 |        29 M |           28 M | 29463-7 |  1.3 M |       35 |
| E5-2687W v4 |      128 |            30 |               10 |       292 M |          278 M | 17861-6 |  1.7 M |       48 |
| E5-2687W v4 |      128 |            30 |               10 |       292 M |          278 M | 39156-5 |  9.7 M |      284 |
| E5-2687W v4 |      128 |            30 |               10 |       292 M |          278 M | 29463-7 |   13 M |      410 |
| EPYC 7543P  |      128 |             4 |                1 |        29 M |           28 M | 17861-6 |  171 k |    4.178 |
| EPYC 7543P  |      128 |             4 |                1 |        29 M |           28 M | 39156-5 |  967 k |   24.492 |
| EPYC 7543P  |      128 |             4 |                1 |        29 M |           28 M | 29463-7 |  1.3 M |   32.126 |
| EPYC 7543P  |      128 |            30 |               10 |       292 M |          278 M | 17861-6 |  1.7 M |   44.994 |
| EPYC 7543P  |      128 |            30 |               10 |       292 M |          278 M | 39156-5 |  9.7 M |  252.942 |
| EPYC 7543P  |      128 |            30 |               10 |       292 M |          278 M | 29463-7 |   13 M |  343.950 |

According to the measurements the time needed by Blaze to deliver resources only depends on the number of hits and equals roughly in **30 seconds per 1 million hits**.

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
