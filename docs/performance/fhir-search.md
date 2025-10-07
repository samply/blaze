# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code at a rate of **6 million resources per second** and export the matching resources at **150,000 resources per second**. This performance is independent of the total number of resources held in the database.

## Systems

The following systems, with increasing resources, were used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM |    SSD |
|--------|----------|-------------|------:|--------:|-------:|
| LEA25  | on-prem  | EPYC 7543P  |     4 |  32 GiB |   2 TB | 
| LEA36  | on-prem  | EPYC 7543P  |     8 |  64 GiB |   2 TB | 
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB |   2 TB | 
| LEA58  | on-prem  | EPYC 7543P  |    32 | 256 GiB |   2 TB | 
| CCX42  | Hetzner  | EPYC 7763   |    16 |  64 GiB | 360 GB |  
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB |   2 TB |

All systems were configured according to the [Tuning Guide](../tuning-guide.md).

On all systems, the heap memory and the block cache were each configured to use 1/4 of the total available RAM. Consequently, the Blaze process itself uses about half of the available system memory, leaving the remainder for the file system cache.

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Size on SSD |
|---------|----------|---------:|---------:|---------:|------------:|
| 100k    | 10 years |    100 k |    104 M |     59 M |     202 GiB |
| 1M      | 10 years |      1 M |   1044 M |    593 M |    1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

The creation of these datasets is described in the [Synthea section](./synthea/README.md). The disk size was measured after a full manual compaction of the database. The actual disk size can be up to 50% higher, depending on the state of the background compaction process.

## Simple Code Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 8310-5  |  115 k |     0.06 |  0.003 |  1.92 M |
| LEA36  | 100k    | 55758-7 |  1.0 M |     0.49 |  0.005 |  2.04 M |
| LEA36  | 100k    | 72514-3 |  2.7 M |     1.28 |  0.022 |  2.11 M |
| LEA47  | 100k    | 8310-5  |  115 k |     0.06 |  0.001 |  1.92 M |
| LEA47  | 100k    | 55758-7 |  1.0 M |     0.50 |  0.010 |  2.00 M |
| LEA47  | 100k    | 72514-3 |  2.7 M |     1.34 |  0.026 |  2.01 M |
| LEA58  | 100k    | 8310-5  |  115 k |     0.07 |  0.002 |  1.64 M |
| LEA58  | 100k    | 55758-7 |  1.0 M |     0.54 |  0.020 |  1.85 M |
| LEA58  | 100k    | 72514-3 |  2.7 M |     1.43 |  0.045 |  1.89 M |
| LEA47  | 1M      | 8310-5  |  1.1 M |     0.34 |  0.003 |   3.4 M |
| LEA47  | 1M      | 55758-7 | 10.1 M |     3.15 |  0.038 |   3.2 M |
| LEA47  | 1M      | 72514-3 | 27.3 M |     8.32 |  0.075 |   3.3 M |
| LEA58  | 1M      | 8310-5  |  1.1 M |     0.39 |  0.009 |   3.0 M |
| LEA58  | 1M      | 55758-7 | 10.1 M |     2.84 |  0.026 |   3.6 M |
| LEA58  | 1M      | 72514-3 | 27.3 M |     7.52 |  0.138 |   3.6 M |
| A5N46  | 1M      | 8310-5  |  1.1 M |     0.20 |  0.008 |   5.8 M |
| A5N46  | 1M      | 55758-7 | 10.1 M |     2.35 |  0.016 |   4.3 M |
| A5N46  | 1M      | 72514-3 | 27.3 M |     4.75 |  0.074 |   5.8 M |

¹ resources per second

### Downloading Resources

![](fhir-search/simple-code-search-download-1M.png)

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|---------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.90 |  0.016 |  60.53 k |  
| LEA36  | 100k    | 55758-7 |  1.0 M |    15.45 |  0.121 |  64.72 k |
| LEA36  | 100k    | 72514-3 |  2.7 M |    41.09 |  0.530 |  65.71 k |
| LEA47  | 100k    | 8310-5  |  115 k |     2.00 |  0.021 |  57.50 k |  
| LEA47  | 100k    | 55758-7 |  1.0 M |    15.99 |  0.147 |  62.54 k |
| LEA47  | 100k    | 72514-3 |  2.7 M |    43.48 |  0.128 |  62.10 k |
| LEA58  | 100k    | 8310-5  |  115 k |     1.96 |  0.039 |  58.67 k |  
| LEA58  | 100k    | 55758-7 |  1.0 M |    16.61 |  0.161 |  60.20 k |
| LEA58  | 100k    | 72514-3 |  2.7 M |    43.84 |  0.124 |  61.59 k |         
| LEA47  | 1M      | 8310-5  |  1.1 M |    15.43 |  0.034 |   75.1 k |         
| LEA47  | 1M      | 55758-7 | 10.1 M |   155.78 |  0.976 | 65.1 k ² |         
| LEA47  | 1M      | 72514-3 | 10.1 M |   114.41 |  0.438 | 88.6 k ² |
| LEA58  | 1M      | 8310-5  |  1.1 M |    15.35 |  0.135 |   75.5 k |         
| LEA58  | 1M      | 55758-7 | 10.1 M |   136.48 |  0.236 |   74.3 k |         
| LEA58  | 1M      | 72514-3 | 27.3 M |   416.05 | 22.655 | 65.7 k ² |
| A5N46  | 1M      | 8310-5  |  1.1 M |     7.78 |  0.040 |  148.9 k |         
| A5N46  | 1M      | 55758-7 | 10.1 M |   148.39 |  1.776 | 68.3 k ² |        
| A5N46  | 1M      | 72514-3 | 27.3 M |   413.87 |  0.165 | 66.1 k ² |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |   Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|----------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.26 |  0.009 |   91.27 k |
| LEA36  | 100k    | 55758-7 |  1.0 M |     9.70 |  0.070 |  103.09 k |
| LEA36  | 100k    | 72514-3 |  2.7 M |    25.82 |  0.440 |  104.57 k |
| LEA47  | 100k    | 8310-5  |  115 k |     1.34 |  0.009 |   85.82 k |
| LEA47  | 100k    | 55758-7 |  1.0 M |     9.95 |  0.065 |  100.50 k |
| LEA47  | 100k    | 72514-3 |  2.7 M |    26.76 |  0.284 |  100.90 k |
| LEA58  | 100k    | 8310-5  |  115 k |     1.28 |  0.017 |   89.84 k |
| LEA58  | 100k    | 55758-7 |  1.0 M |    10.55 |  0.209 |   94.79 k |
| LEA58  | 100k    | 72514-3 |  2.7 M |    27.15 |  0.749 |   99.45 k |
| LEA47  | 1M      | 8310-5  |  1.1 M |    10.48 |  0.185 |   110.6 k |          
| LEA47  | 1M      | 55758-7 | 10.1 M |   114.41 |  0.438 |  88.6 k ² |          
| LEA47  | 1M      | 72514-3 | 27.3 M |   375.96 |  1.683 |  72.7 k ² |
| LEA58  | 1M      | 8310-5  |  1.1 M |    10.61 |  0.120 |   109.2 k |          
| LEA58  | 1M      | 55758-7 | 10.1 M |    88.05 |  1.587 |   115.2 k |          
| LEA58  | 1M      | 72514-3 | 27.3 M |   259.91 |  2.506 | 105.2 k ² |
| A5N46  | 1M      | 8310-5  |  1.1 M |     5.08 |  0.054 |   228.3 k |          
| A5N46  | 1M      | 55758-7 | 10.1 M |   105.87 |  0.544 |  95.8 k ² |          
| A5N46  | 1M      | 72514-3 | 27.3 M |   318.80 |  7.558 |  85.8 k ² |          

¹ resources per second, ² resource cache size is smaller than the number of resources returned

## Multiple Code Search

This section evaluates the performance of FHIR Search for selecting Observation resources with multiple codes.

The following codes were used:

* 10 LOINC codes from [observation-codes-10.txt](fhir-search/observation-codes-10.txt)

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_summary=count"
```

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 10    | 27.9 M |    11.52 |  0.215 |   2.4 M |

¹ resources per second

### Downloading Resources

![](fhir-search/multiple-code-search-download-1M.png)

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_count=1000" > /dev/null
```

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 10    | 27.9 M |   732.37 | 43.498 |  38.1 k |

¹ resources per second

## Multiple Search Parameter Search

This section evaluates the performance of FHIR search queries with multiple search parameters and multiple codes.

Two sets of codes were used:

| Category    | Name  | Codes                                     |
|-------------|-------|-------------------------------------------|
| laboratory  | top-5 | 49765-1, 20565-8, 2069-3, 38483-4, 2339-0 |
| vital-signs | low-5 | 2713-6, 8478-0, 8310-5, 77606-2, 9843-4   |

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?status=final&category=$CATEGORY&code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_summary=count"
```

| System | Dataset | Category    | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | laboratory  | top-5 | 90.3 M |   401.86 |  1.521 | 224.6 k |
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |     5.01 |  0.036 | 703.3 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |   352.50 |  8.995 | 256.1 k |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |     1.43 |  0.018 |   2.5 M |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |   157.81 |  1.402 | 572.0 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |     2.61 |  0.008 |   1.4 M |

¹ resources per second

### Downloading Resources

![](fhir-search/multiple-search-param-search-download-1M.png)

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "status=final&category=$CATEGORY&code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_count=1000" > /dev/null
```

| System | Dataset | Category    | Codes | # Hits | Time (s) |  StdDev |  Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|--------:|---------:|
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |   113.88 |   1.961 |   30.9 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |  2490.08 | 101.868 | 36.2 k ² |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |    62.43 |   0.356 |   56.5 k |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |  1494.28 |  11.798 | 60.4 k ² |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |    52.61 |   0.045 |   67.0 k |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

All measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "status=final&category=$CATEGORY&code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Category    | Codes | # Hits | Time (s) |  StdDev |  Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|--------:|---------:|
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |   106.20 |   6.090 |   33.2 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |  2200.60 | 232.968 | 41.0 k ² |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |    54.94 |   5.827 |   64.2 k |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |  1248.41 | 190.598 | 72.3 k ² |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |    48.67 |   3.052 |   72.4 k |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

## Code and Value Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code and value.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_summary=count"
```

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 29463-7 |  26.8 |  158 k |     5.81 |  0.061 | 36.75 ² |
| LEA36  | 100k    | 29463-7 |  79.5 |  790 k |     5.94 |  0.019 |  7.52 ² |
| LEA36  | 100k    | 29463-7 |   183 |  1.6 M |     5.88 |  0.030 |  3.71 ² |
| LEA47  | 100k    | 29463-7 |  26.8 |  158 k |     0.85 |  0.006 |    5.38 |
| LEA47  | 100k    | 29463-7 |  79.5 |  790 k |     0.84 |  0.005 |    1.06 |
| LEA47  | 100k    | 29463-7 |   183 |  1.6 M |     0.85 |  0.007 |    0.54 |
| LEA58  | 100k    | 29463-7 |  26.8 |  158 k |     0.87 |  0.010 |    5.53 |
| LEA58  | 100k    | 29463-7 |  79.5 |  790 k |     0.87 |  0.014 |    1.10 |
| LEA58  | 100k    | 29463-7 |   183 |  1.6 M |     0.89 |  0.005 |    0.55 |
| LEA58  | 1M      | 29463-7 |  26.8 |  1.6 M |    17.89 |  0.397 |   11.45 |
| LEA58  | 1M      | 29463-7 |  79.5 |  7.8 M |    17.69 |  0.167 |    2.25 |
| LEA58  | 1M      | 29463-7 |   183 | 15.9 M |    17.53 |  0.110 |    1.10 |
| A5N46  | 1M      | 29463-7 |  26.8 |  1.6 M |    56.84 |  2.925 |  27.5 k |

¹ time in seconds per 1 million resources, ² block cache hit ratio is near zero

### Downloading Resources

All measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:
 
```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_count=1000" > /dev/null
```

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev |   T/1M ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|---------:|
| LEA36  | 100k    | 29463-7 |  26.8 |  158 k |    43.24 |  0.048 | 273.51 ² |
| LEA36  | 100k    | 29463-7 |  79.5 |  790 k |    52.30 |  0.181 |  66.24 ² |
| LEA36  | 100k    | 29463-7 |   183 |  1.6 M |    63.81 |  0.057 |  40.32 ² |
| LEA47  | 100k    | 29463-7 |  26.8 |  158 k |    12.84 |  0.016 |    81.22 |
| LEA47  | 100k    | 29463-7 |  79.5 |  790 k |    21.83 |  0.128 |    27.64 |
| LEA47  | 100k    | 29463-7 |   183 |  1.6 M |    32.86 |  0.291 |    20.76 |
| LEA58  | 100k    | 29463-7 |  26.8 |  158 k |    12.78 |  0.025 |    80.82 |
| LEA58  | 100k    | 29463-7 |  79.5 |  790 k |    21.95 |  0.193 |    27.80 |
| LEA58  | 100k    | 29463-7 |   183 |  1.6 M |    31.63 |  0.333 |    19.99 |
| A5N46  | 1M      | 29463-7 |  26.8 |  1.6 M |    44.78 |  3.954 |   34.9 k |

¹ time in seconds per 1 million resources, ² block cache hit ratio is near zero

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

All measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev |   T/1M ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|---------:|
| LEA36  | 100k    | 29463-7 |  26.8 |  158 k |    42.38 |  0.104 | 268.09 ² |
| LEA36  | 100k    | 29463-7 |  79.5 |  790 k |    48.29 |  0.071 |  61.15 ² |
| LEA36  | 100k    | 29463-7 |   183 |  1.6 M |    55.47 |  0.131 |  35.05 ² |
| LEA47  | 100k    | 29463-7 |  26.8 |  158 k |    11.98 |  0.029 |    75.78 |
| LEA47  | 100k    | 29463-7 |  79.5 |  790 k |    17.38 |  0.095 |    22.01 |
| LEA47  | 100k    | 29463-7 |   183 |  1.6 M |    23.44 |  0.177 |    14.81 |
| LEA58  | 100k    | 29463-7 |  26.8 |  158 k |    11.90 |  0.021 |    75.25 |
| LEA58  | 100k    | 29463-7 |  79.5 |  790 k |    17.22 |  0.047 |    21.80 |
| LEA58  | 100k    | 29463-7 |   183 |  1.6 M |    23.18 |  0.217 |    14.64 |

¹ time in seconds per 1 million resources, ² block cache hit ratio is near zero

## Code and Date Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code and date.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&date=$DATE&_summary=count"
```

| System | Dataset | Code    | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-----:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  | 2013 |   50 k |     0.58 |  0.003 |  86.6 k |
| LEA47  | 1M      | 8310-5  | 2019 |   85 k |     0.59 |  0.003 | 145.3 k |
| LEA47  | 1M      | 8310-5  | 2020 |  273 k |     0.59 |  0.003 | 465.8 k |
| A5N46  | 1M      | 8310-5  | 2013 |   50 k |     0.27 |  0.011 | 185.0 k | 
| A5N46  | 1M      | 8310-5  | 2019 |   85 k |     0.28 |  0.012 | 305.4 k | 
| A5N46  | 1M      | 8310-5  | 2020 |  273 k |     0.29 |  0.008 | 937.6 k |
| A5N46  | 1M      | 55758-7 | 2013 |  549 k |    44.97 |  0.196 |  12.2 k | 
| A5N46  | 1M      | 55758-7 | 2019 |  1.0 M |    46.29 |  0.067 |  22.5 k | 
| A5N46  | 1M      | 55758-7 | 2020 |  1.1 M |    46.29 |  0.028 |  23.1 k |
| A5N46  | 1M      | 72514-3 | 2013 |  1.4 M |    58.62 |  1.129 |  24.6 k | 
| A5N46  | 1M      | 72514-3 | 2019 |  2.8 M |    56.76 |  0.269 |  50.1 k | 
| A5N46  | 1M      | 72514-3 | 2020 |  2.9 M |    56.13 |  0.088 |  52.4 k |

¹ resources per second

### Downloading Resources

All measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=$DATE&_count=1000" > /dev/null
```

| System | Dataset | Code    | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-----:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  | 2013 |   50 k |     8.45 |  0.014 |   6.0 k |
| LEA47  | 1M      | 8310-5  | 2019 |   85 k |     9.10 |  0.031 |   9.4 k |
| LEA47  | 1M      | 8310-5  | 2020 |  273 k |    12.20 |  0.111 |  22.4 k |
| A5N46  | 1M      | 8310-5  | 2013 |   50 k |     3.29 |  0.009 |  15.3 k |
| A5N46  | 1M      | 8310-5  | 2019 |   85 k |     3.68 |  0.009 |  23.1 k |
| A5N46  | 1M      | 8310-5  | 2020 |  273 k |     5.61 |  0.012 |  48.7 k |
| A5N46  | 1M      | 55758-7 | 2013 |  549 k |   346.06 |  1.040 |   1.6 k |
| A5N46  | 1M      | 55758-7 | 2019 |  1.0 M |   379.45 |  4.408 |   2.7 k |
| A5N46  | 1M      | 55758-7 | 2020 |  1.1 M |   378.09 |  0.520 |   2.8 k |
| A5N46  | 1M      | 72514-3 | 2013 |  1.4 M |   543.21 |  0.268 |   2.7 k |
| A5N46  | 1M      | 72514-3 | 2019 |  2.8 M |   625.51 |  0.536 |   4.5 k |
| A5N46  | 1M      | 72514-3 | 2020 |  2.9 M |   669.83 |  7.711 |   4.4 k |

¹ resources per second

## Category and Date Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific category and date.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?category=$CATEGORY&date=$DATE&_summary=count"
```

| System | Dataset | Code        | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|-----:|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | laboratory  | 2013 | 19.6 M |   101.11 |  0.148 | 194.1 k | 
| A5N46  | 1M      | laboratory  | 2019 | 38.4 M |   103.22 |  0.188 | 372.0 k | 
| A5N46  | 1M      | laboratory  | 2020 | 44.9 M |   103.57 |  0.344 | 433.5 k |
| A5N46  | 1M      | vital-signs | 2013 |  7.6 M |    70.43 |  1.011 | 107.9 k | 
| A5N46  | 1M      | vital-signs | 2019 | 14.3 M |    71.14 |  0.904 | 201.4 k | 
| A5N46  | 1M      | vital-signs | 2020 | 15.8 M |    72.11 |  0.678 | 219.0 k |

## Code and Patient Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code for 1000 patients.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.02 |  0.003 |  39.7 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.09 |  0.008 | 301.7 k |
| LEA47  | 1M      | 72514-3 |  113 k |     0.23 |  0.007 | 484.8 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.02 |  0.002 |  39.8 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.10 |  0.004 | 288.3 k |
| LEA58  | 1M      | 72514-3 |  113 k |     0.25 |  0.014 | 456.2 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.001 |  82.0 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.05 |  0.001 | 590.1 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.12 |  0.003 | 938.8 k |

¹ resources per second

### Downloading Resources

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.06 |  0.000 |  15.7 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.45 |  0.012 |  62.0 k |
| LEA47  | 1M      | 72514-3 |  113 k |     1.58 |  0.005 |  71.4 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.06 |  0.005 |  16.6 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.48 |  0.009 |  59.0 k |
| LEA58  | 1M      | 72514-3 |  113 k |     1.57 |  0.009 |  71.9 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.03 |  0.000 |  31.5 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.35 |  0.005 |  79.7 k |
| A5N46  | 1M      | 72514-3 |  113 k |     1.25 |  0.017 |  90.3 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.03 |  0.000 |  31.4 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.32 |  0.008 |  87.9 k |
| LEA47  | 1M      | 72514-3 |  113 k |     1.09 |  0.021 | 104.1 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.03 |  0.005 |  28.3 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.32 |  0.005 |  88.8 k |
| LEA58  | 1M      | 72514-3 |  113 k |     1.07 |  0.008 | 105.7 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.005 |  70.8 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.22 |  0.009 | 126.1 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.75 |  0.008 | 150.9 k |

¹ resources per second

## Multiple Codes and Patient Search

This section evaluates the performance of FHIR Search for selecting Observation and Condition resources with multiple codes for 1000 patients.

The following codes were used:

* 10 LOINC codes from [observation-codes-10.txt](fhir-search/observation-codes-10.txt)
* 100 LOINC codes from [observation-codes-100.txt](fhir-search/observation-codes-100.txt)
* 1k SNOMED CT codes from [condition-codes-disease-1k.txt](fhir-search/condition-codes-disease-1k.txt)
* 10k SNOMED CT codes from [condition-codes-disease-10k.txt](fhir-search/condition-codes-disease-10k.txt)

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |    10 |   46 k |     0.14 |  0.006 | 333.1 k |
| LEA47  | 1M      |   100 |  1.1 M |     2.38 |  0.070 | 461.7 k |
| LEA47  | 1M      |    1k |    247 |     3.04 |  0.040 |      81 |
| LEA47  | 1M      |   10k |    1 k |    43.17 |  0.487 |      29 |
| A5N46  | 1M      |    10 |   46 k |     0.07 |  0.003 | 658.9 k |
| A5N46  | 1M      |   100 |  1.1 M |     1.18 |  0.004 | 933.1 k |
| A5N46  | 1M      |    1k |    247 |     1.35 |  0.008 |     184 |
| A5N46  | 1M      |   10k |    1 k |    20.69 |  0.133 |      61 |

¹ resources per second

### Downloading Resources

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&patient=$PATIENT_IDS&_count=1000" > /dev/null
```

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |    10 |   46 k |     0.75 |  0.071 |  61.7 k |
| LEA47  | 1M      |   100 |  1.1 M |    15.52 |  0.187 |  70.7 k |
| LEA47  | 1M      |    1k |    247 |     3.01 |  0.024 |      82 |
| LEA47  | 1M      |   10k |    1 k |    41.68 |  0.095 |      30 |
| A5N46  | 1M      |    10 |   46 k |     0.36 |  0.005 | 129.7 k |
| A5N46  | 1M      |   100 |  1.1 M |     7.21 |  0.026 | 152.2 k |
| A5N46  | 1M      |    1k |    247 |     1.34 |  0.000 |     184 |
| A5N46  | 1M      |   10k |    1 k |    20.61 |  0.141 |      61 |

¹ resources per second

## Code, Date, and Patient Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code, a specific date, and for 1000 patients.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.03 |  0.002 |   8.4 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.15 |  0.003 |  18.9 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.43 |  0.005 |  27.9 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.001 |  18.0 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.08 |  0.002 |  36.0 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.22 |  0.003 |  54.3 k |

¹ resources per second

### Downloading Resources

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.04 |  0.005 |   6.7 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.21 |  0.005 |  13.6 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.62 |  0.000 |  19.3 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.02 |  0.005 |  14.8 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.13 |  0.000 |  21.7 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.37 |  0.005 |  32.0 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.03 |  0.000 |   8.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.18 |  0.005 |  15.4 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.54 |  0.009 |  22.3 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.000 |  24.6 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.11 |  0.000 |  25.7 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.31 |  0.005 |  38.2 k |

¹ resources per second

## Simple Date Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific effective year.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?date=$YEAR&_summary=count"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 2013 |  3.1 M |     2.12 |  0.023 |    0.67 |
| LEA36  | 100k    | 2019 |  6.0 M |     3.81 |  0.062 |    0.63 |
| LEA47  | 100k    | 2013 |  3.1 M |     2.13 |  0.068 |    0.68 |
| LEA47  | 100k    | 2019 |  6.0 M |     4.17 |  0.175 |    0.69 |
| LEA58  | 100k    | 2013 |  3.1 M |     2.22 |  0.071 |    0.71 |
| LEA58  | 100k    | 2019 |  6.0 M |     4.19 |  0.056 |    0.70 |
| LEA47  | 1M      | 2013 | 31.1 M |    21.28 |  0.439 |    0.68 |
| LEA47  | 1M      | 2019 | 60.0 M |    42.58 |  1.502 |    0.70 |
| A5N46  | 1M      | 2013 | 31.1 M |    57.78 |  0.257 | 537.8 k |
| A5N46  | 1M      | 2019 | 60.0 M |   111.38 |  0.426 | 539.2 k |

¹ resources per second

### Downloading Resources

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_count=1000" > /dev/null
```

| System | Dataset | Year | # Hits | Time (s) | StdDev |  Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|---------:|
| LEA36  | 100k    | 2013 |  3.1 M |    52.42 |  0.545 |    16.77 |
| LEA36  | 100k    | 2019 |  6.0 M |   129.84 |  5.393 |  21.71 ² |
| LEA47  | 100k    | 2013 |  3.1 M |    53.41 |  0.377 |    17.08 |
| LEA47  | 100k    | 2019 |  6.0 M |   107.15 |  0.116 |    17.92 |
| LEA58  | 100k    | 2013 |  3.1 M |    53.07 |  0.090 |    16.98 |
| LEA58  | 100k    | 2019 |  6.0 M |   100.73 |  0.473 |    16.84 |
| LEA47  | 1M      | 2013 | 31.1 M |   991.28 | 12.329 |  31.90 ² |
| LEA47  | 1M      | 2019 | 60.0 M |  2083.44 | 31.983 |  34.69 ² |
| A5N46  | 1M      | 2013 | 31.1 M |   496.84 | 10.766 | 62.5 k ² |
| A5N46  | 1M      | 2019 | 60.0 M |   966.78 |  8.692 | 62.1 k ² |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

Most measurements were taken after Blaze reached a steady state, with all resources to be downloaded held in its resource cache. This was done to eliminate the influence of resource load times from disk or the file system cache.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Year | # Hits | Time (s) | StdDev |   Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|----------:|
| LEA36  | 100k    | 2013 |  3.1 M |    32.07 |  0.278 |     10.26 |
| LEA36  | 100k    | 2019 |  6.0 M |    79.92 |  4.179 |     13.36 |
| LEA47  | 100k    | 2013 |  3.1 M |    31.99 |  0.061 |     10.23 |
| LEA47  | 100k    | 2019 |  6.0 M |    66.33 |  0.045 |     11.09 |
| LEA58  | 100k    | 2013 |  3.1 M |    32.43 |  0.340 |     10.37 |
| LEA58  | 100k    | 2019 |  6.0 M |    62.14 |  0.488 |     10.39 |
| LEA47  | 1M      | 2013 | 31.1 M |   673.36 | 10.199 |   21.67 ² |
| LEA47  | 1M      | 2019 | 60.0 M |  1516.90 |  0.482 |   25.25 ² |
| A5N46  | 1M      | 2013 | 31.1 M |   309.83 |  2.383 | 100.3 k ² |
| A5N46  | 1M      | 2019 | 60.0 M |   619.77 |  6.438 |  96.9 k ² |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

## Patient Date Search

This section evaluates the performance of FHIR Search for selecting Patient resources with a specific birth date.

### Counting

Counting was performed using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Patient?birthdate=$DATE&_summary=count"
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     0.38 |  0.005 |    1.68 |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     0.40 |  0.007 |    1.74 |
| LEA47  | 1M      | lt1998-04-10 |  773 k |     0.58 |  0.017 |    0.75 |
| LEA47  | 1M      | le1998-04-10 |  773 k |     0.60 |  0.005 |    0.78 |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     0.51 |  0.004 | 443.8 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     0.53 |  0.007 | 432.2 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     1.59 |  0.022 | 485.9 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     1.59 |  0.018 | 485.0 k |

¹ resources per second

### Downloading Resources

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q "birthdate=$DATE&_count=1000" > /dev/null
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     7.77 |  0.033 |   34.17 |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     7.91 |  0.056 |   34.77 |
| LEA47  | 1M      | lt1998-04-10 |  773 k |    26.85 |  0.065 |   34.74 |
| LEA47  | 1M      | le1998-04-10 |  773 k |    27.73 |  0.012 |   35.88 |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     5.97 |  0.045 |  38.1 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     5.99 |  0.033 |  38.0 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |    21.41 |  0.074 |  36.1 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |    21.54 |  0.117 |  35.9 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=id` was used.

Downloads were performed using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q "birthdate=$DATE&_elements=id&_count=1000" > /dev/null
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     3.15 |  0.016 |   13.85 |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     3.09 |  0.108 |   13.58 |
| LEA47  | 1M      | lt1998-04-10 |  773 k |     9.90 |  0.249 |   12.81 |
| LEA47  | 1M      | le1998-04-10 |  773 k |     9.73 |  0.073 |   12.59 |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     1.68 |  0.042 | 135.1 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     1.70 |  0.016 | 133.8 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     5.53 |  0.255 | 139.8 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     5.51 |  0.159 | 140.3 k |

¹ resources per second

## Used Dataset

The dataset was generated with Synthea v3.1.1. The resource generation process is described [here](synthea/README.md).

## Controlling and Monitoring the Caches

The size of the resource cache can be set with the `DB_RESOURCE_CACHE_SIZE` environment variable, which specifies the number of resources to cache. It is important to know the memory footprint of a resource, as it can vary widely. Monitoring heap usage is critical.

### Monitoring 

Blaze exposes a Prometheus monitoring endpoint on port 8081 by default. The ideal setup is to connect a Prometheus instance to it and use Grafana for visualization. However, for simple, ad-hoc queries about the current state of Blaze, `curl` and `grep` are sufficient.

#### Java Heap Size

The bytes currently used by the various generations of the Java heap are provided by the `jvm_memory_pool_bytes_used` metric. Of these, the `G1 Old Gen` is the most important, as cached resources are stored there. The following command fetches all metrics and filters for the relevant line:

```sh
curl -s http://localhost:8081/metrics | grep jvm_memory_pool_bytes_used | grep Old
jvm_memory_pool_bytes_used{pool="G1 Old Gen",} 8.325004288E9
```

Here, the value `8.325004288E9` is in bytes, and `E9` indicates gigabytes. This shows 8.3 GB of the old generation are in use, which constitutes most of the total heap size. If Blaze were configured with a maximum heap size of 10 GB, this usage would be within a healthy upper limit.

#### Resource Cache

Resource cache metrics are available under keys starting with `blaze_db_cache`. Among these is the `resource-cache`. These metrics can be difficult to interpret without a Prometheus/Grafana infrastructure because they are counters that accumulate since Blaze was started. Therefore, after a long runtime, it is necessary to calculate relative differences. However, shortly after Blaze starts, the absolute numbers are very useful on their own.

```sh
curl -s http://localhost:8081/metrics | grep blaze_db_cache | grep resource-cache
blaze_db_cache_hits_total{name="resource-cache",} 869000.0
blaze_db_cache_loads_total{name="resource-cache",} 13214.0
blaze_db_cache_load_failures_total{name="resource-cache",} 0.0
blaze_db_cache_load_seconds_total{name="resource-cache",} 234.418864426
blaze_db_cache_evictions_total{name="resource-cache",} 0.0
```

The number of evictions is a key metric here. As long as the number of evictions is zero, the resource cache has not overflowed. The goal should be for most CQL or FHIR Search queries with exports to fit within the resource cache. If the number of resources in a single query exceeds the cache size, the cache will be evicted and refilled during that query. This is especially problematic for repeated queries, as resources needed at the beginning of the query will no longer be in the cache after it is refilled. Therefore, a cache size smaller than what is required for a single query offers no performance benefit.

[1]: <https://www.hl7.org/fhir/search.html#elements>
