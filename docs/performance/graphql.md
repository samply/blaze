# GraphQL Performance

## Simple Code Search

In this section, GraphQL for selecting Observation resources with a certain code is used.

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `curl` command:

```sh
curl -s -H "Content-Type: application/graphql" -d "{ ObservationList { subject { reference } } }" "http://localhost:8080/\$graphql" > /dev/null"
```

| CPU        | Heap Mem | Block Cache | # Res. ¹ | # Obs. ² | Code    | # Hits | Time (s) | T / 1M ³ |
|------------|---------:|------------:|---------:|---------:|---------|-------:|---------:|---------:|
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 17861-6 |  171 k |    1.045 |     6.11 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 39156-5 |  967 k |    5.740 |     5.94 |
| EPYC 7543P |     8 GB |        1 GB |     29 M |     28 M | 29463-7 |  1.3 M |    8.057 |     6.20 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 17861-6 |  1.7 M |   10.744 |     6.32 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 39156-5 |  9.7 M |   70.122 |     7.23 |
| EPYC 7543P |    30 GB |       10 GB |    292 M |    278 M | 29463-7 |   13 M |   96.735 |     7.44 |

¹ Number of Resources, ² Number of Observations, ³ Time in seconds per 1 million resources, The amount of system memory was 128 GB in all cases.

According to the measurements, the time needed by Blaze to deliver Observations containing only the subject reference is about **twice as fast** as returning the same information via [Subsetted FHIR Search](fhir-search.md#download-of-resources-with-subsetting) and **4 times as fast** as downloading the whole Observation Resources using [FHIR Search](fhir-search.md#download-of-resources).

## Used Dataset

The dataset was the same as in [FHIR Search](fhir-search.md) performance tests.
