# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code at a rate of **6 million resources per second** and export the matching resources at **150,000 resources per second**. This performance is independent of the total number of resources held in the database.

## Systems

The following systems, with increasing resources, were used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM |  SSD |
|--------|----------|-------------|------:|--------:|-----:|
| LEA25  | on-prem  | EPYC 7543P  |     4 |  32 GiB | 2 TB |
| LEA36  | on-prem  | EPYC 7543P  |     8 |  64 GiB | 2 TB |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 2 TB |
| LEA58  | on-prem  | EPYC 7543P  |    32 | 256 GiB | 2 TB |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 2 TB |
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 2 TB |

All systems were configured according to the [Production Configuration](../production-configuration.md) Guide.

On all systems, the heap memory and the block cache were each configured to use 1/4 of the total available RAM. Consequently, the Blaze process itself uses about half of the available system memory, leaving the remainder for the file system cache.

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Size on SSD |
|---------|----------|---------:|---------:|---------:|------------:|
| 100k    | 10 years |    100 k |    104 M |     59 M |     110 GiB |
| 1M      | 10 years |      1 M |   1044 M |    593 M |    1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

The creation of these datasets is described in the [Synthea section](./synthea/README.md). The disk size was measured after a full manual compaction of the database. The actual disk size can be up to 50% higher, depending on the state of the background compaction process.

## Simple Code Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code.

### Script

The script `simple-code-search.sh` is used.

### Counting

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
| LEA47  | 1M      | 8310-5  |  1.1 M |     0.37 |  0.005 |   3.1 M |
| LEA47  | 1M      | 55758-7 | 10.1 M |     3.34 |  0.065 |   3.0 M |
| LEA47  | 1M      | 72514-3 | 27.3 M |     8.72 |  0.051 |   3.1 M |
| LEA58  | 1M      | 8310-5  |  1.1 M |     0.39 |  0.009 |   3.0 M |
| LEA58  | 1M      | 55758-7 | 10.1 M |     2.84 |  0.026 |   3.6 M |
| LEA58  | 1M      | 72514-3 | 27.3 M |     7.52 |  0.138 |   3.6 M |
| LEA79  | 1M      | 8310-5  |  1.1 M |     0.28 |  0.004 |   4.1 M |
| LEA79  | 1M      | 55758-7 | 10.1 M |     1.44 |  0.022 |   7.1 M |
| LEA79  | 1M      | 72514-3 | 27.3 M |     4.38 |  0.192 |   6.2 M |
| A5N46  | 1M      | 8310-5  |  1.1 M |     0.20 |  0.008 |   5.8 M |
| A5N46  | 1M      | 55758-7 | 10.1 M |     2.35 |  0.016 |   4.3 M |
| A5N46  | 1M      | 72514-3 | 27.3 M |     4.75 |  0.074 |   5.8 M |

¹ resources per second

### Downloading Resources

![](fhir-search/simple-code-search-download-1M.png)

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.90 |  0.016 | 60.53 k |  
| LEA36  | 100k    | 55758-7 |  1.0 M |    15.45 |  0.121 | 64.72 k |
| LEA36  | 100k    | 72514-3 |  2.7 M |    41.09 |  0.530 | 65.71 k |
| LEA47  | 100k    | 8310-5  |  115 k |     2.00 |  0.021 | 57.50 k |  
| LEA47  | 100k    | 55758-7 |  1.0 M |    15.99 |  0.147 | 62.54 k |
| LEA47  | 100k    | 72514-3 |  2.7 M |    43.48 |  0.128 | 62.10 k |
| LEA58  | 100k    | 8310-5  |  115 k |     1.96 |  0.039 | 58.67 k |  
| LEA58  | 100k    | 55758-7 |  1.0 M |    16.61 |  0.161 | 60.20 k |
| LEA58  | 100k    | 72514-3 |  2.7 M |    43.84 |  0.124 | 61.59 k |         
| LEA47  | 1M      | 8310-5  |  1.1 M |    21.44 |  0.260 |  54.0 k |         
| LEA47  | 1M      | 55758-7 | 10.1 M |   322.95 | 37.205 |  31.4 k |         
| LEA47  | 1M      | 72514-3 | 27.3 M |   828.67 |  0.968 |  33.0 k |
| LEA58  | 1M      | 8310-5  |  1.1 M |    20.70 |  0.320 |  56.0 k |         
| LEA58  | 1M      | 55758-7 | 10.1 M |   171.60 |  4.241 |  59.1 k |         
| LEA58  | 1M      | 72514-3 | 27.3 M |   497.29 | 26.969 |  55.0 k |
| LEA79  | 1M      | 8310-5  |  1.1 M |    26.85 |  0.052 |  43.2 k |         
| LEA79  | 1M      | 55758-7 | 10.1 M |   233.25 |  0.202 |  43.5 k |         
| LEA79  | 1M      | 72514-3 | 27.3 M |   632.51 |  0.728 |  43.2 k |
| A5N46  | 1M      | 8310-5  |  1.1 M |    10.55 |  0.034 | 109.8 k |         
| A5N46  | 1M      | 55758-7 | 10.1 M |   121.64 |  0.312 |  83.3 k |        
| A5N46  | 1M      | 72514-3 | 27.3 M |   294.11 |  0.523 |  93.0 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|---------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.26 |  0.009 |  91.27 k |
| LEA36  | 100k    | 55758-7 |  1.0 M |     9.70 |  0.070 | 103.09 k |
| LEA36  | 100k    | 72514-3 |  2.7 M |    25.82 |  0.440 | 104.57 k |
| LEA47  | 100k    | 8310-5  |  115 k |     1.34 |  0.009 |  85.82 k |
| LEA47  | 100k    | 55758-7 |  1.0 M |     9.95 |  0.065 | 100.50 k |
| LEA47  | 100k    | 72514-3 |  2.7 M |    26.76 |  0.284 | 100.90 k |
| LEA58  | 100k    | 8310-5  |  115 k |     1.28 |  0.017 |  89.84 k |
| LEA58  | 100k    | 55758-7 |  1.0 M |    10.55 |  0.209 |  94.79 k |
| LEA58  | 100k    | 72514-3 |  2.7 M |    27.15 |  0.749 |  99.45 k |
| LEA47  | 1M      | 8310-5  |  1.1 M |    16.43 |  0.570 |   70.5 k |          
| LEA47  | 1M      | 55758-7 | 10.1 M |   377.69 |  1.794 |   26.8 k |          
| LEA47  | 1M      | 72514-3 | 27.3 M |   704.78 |  2.102 |   38.8 k |
| LEA58  | 1M      | 8310-5  |  1.1 M |    15.42 |  0.208 |   75.1 k |          
| LEA58  | 1M      | 55758-7 | 10.1 M |   124.03 |  1.024 |   81.8 k |          
| LEA58  | 1M      | 72514-3 | 27.3 M |   343.16 |  1.412 |   79.7 k |
| LEA79  | 1M      | 8310-5  |  1.1 M |    18.99 |  0.078 |   61.0 k |          
| LEA79  | 1M      | 55758-7 | 10.1 M |   156.54 |  0.367 |   64.8 k |          
| LEA79  | 1M      | 72514-3 | 27.3 M |   407.08 |  1.076 |   67.2 k |
| A5N46  | 1M      | 8310-5  |  1.1 M |     7.49 |  0.054 |  154.7 k |          
| A5N46  | 1M      | 55758-7 | 10.1 M |    97.79 |  0.061 |  103.7 k |          
| A5N46  | 1M      | 72514-3 | 27.3 M |   231.97 |  0.111 |  117.9 k |          

¹ resources per second

## Multiple Code Search

This section evaluates the performance of FHIR Search for selecting Observation resources with multiple codes.

The following codes were used:

* 10 LOINC codes from `observation-codes-10.txt`
* 100 LOINC codes from `observation-codes-100.txt`

### Script

The script `multiple-code-searchs.sh` is used.

### Counting

| System | Dataset | Codes |  # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|--------:|---------:|-------:|--------:|
| LEA47  | 1M      | 10    |  27.9 M |    11.08 |  0.109 |   2.5 M |
| LEA47  | 1M      | 100   | 322.6 M |   165.30 |  0.985 |   2.0 M |
| LEA58  | 1M      | 10    |  27.9 M |    11.24 |  0.180 |   2.5 M |
| LEA58  | 1M      | 100   | 322.6 M |   171.82 |  1.402 |   1.9 M |
| A5N46  | 1M      | 10    |  27.9 M |     5.28 |  0.032 |   5.3 M |
| A5N46  | 1M      | 100   | 322.6 M |    82.95 |  0.082 |   3.9 M |

¹ resources per second

### Downloading Resources

![](fhir-search/multiple-code-search-download-1M.png)

| System | Dataset | Codes |  # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|--------:|---------:|-------:|--------:|
| LEA47  | 1M      | 10    |  27.9 M |   677.48 |  4.174 |  41.2 k |
| LEA47  | 1M      | 100   | 322.6 M | 11148.16 | 62.879 |  28.9 k |
| LEA58  | 1M      | 10    |  27.9 M |   456.25 |  0.690 |  61.1 k |
| LEA58  | 1M      | 100   | 322.6 M |  9776.25 | 83.038 |  33.0 k |
| A5N46  | 1M      | 10    |  27.9 M |   278.13 |  3.040 | 100.3 k |
| A5N46  | 1M      | 100   | 322.6 M |  4699.36 |  9.647 |  68.6 k |

¹ resources per second

## Multiple Search Parameter Search

This section evaluates the performance of FHIR search queries with multiple search parameters and multiple codes.

Two sets of codes were used:

| Category    | Name  | Codes                                     |
|-------------|-------|-------------------------------------------|
| laboratory  | top-5 | 49765-1, 20565-8, 2069-3, 38483-4, 2339-0 |
| vital-signs | low-5 | 2713-6, 8478-0, 8310-5, 77606-2, 9843-4   |

### Script

The script `observation-final-category-multiple-codes-search.sh` is used.

### Counting

| System | Dataset | Category    | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | laboratory  | top-5 | 90.3 M |   398.47 |  1.039 | 226.5 k |
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |     5.45 |  0.436 | 646.7 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |   352.50 |  8.995 | 256.1 k |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |     1.43 |  0.018 |   2.5 M |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |   172.31 |  0.336 | 523.9 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |     2.65 |  0.019 |   1.3 M |

¹ resources per second

### Downloading Resources

![](fhir-search/multiple-search-param-search-download-1M.png)

| System | Dataset | Category    | Codes | # Hits | Time (s) |  StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|--------:|--------:|
| LEA47  | 1M      | laboratory  | top-5 | 90.3 M |  2923.45 |   7.951 |  30.9 k |
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |   153.07 |  29.422 |  23.0 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |  2490.08 | 101.868 |  36.2 k |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |    62.43 |   0.356 |  56.5 k |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |  1093.04 |   7.293 |  82.6 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |    63.30 |   1.099 |  55.7 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Category    | Codes | # Hits | Time (s) |  StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|--------:|--------:|
| LEA47  | 1M      | laboratory  | top-5 | 90.3 M |  2625.21 |  24.973 |  34.4 k |
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |   118.72 |   0.099 |  29.7 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |  2200.60 | 232.968 |  41.0 k |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |    54.94 |   5.827 |  64.2 k |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |   979.66 |  98.309 |  92.1 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |    58.23 |   4.449 |  60.5 k |

¹ resources per second

## Token and Forward Chaining Search

This section evaluates the performance of FHIR search queries with one badly discriminating token search parameter and one well discriminating forward chaining search parameter.

### Script

The script `token-forward-chaining-search.sh` is used.

### Counting

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      |   32 k |     0.15 |  0.006 | 216.4 k |

¹ resources per second

### Downloading Resources

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      |   32 k |     2.01 |  0.008 |  15.7 k |

¹ resources per second

## Code and Value Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code and value.

### Script

The script `code-value-search.sh` is used.

### Counting

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

### Script

The script `code-date-search.sh` is used.

### Counting

| System | Dataset | Code    | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-----:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  | 2013 |   50 k |     0.42 |  0.006 | 119.1 k | 
| LEA47  | 1M      | 8310-5  | 2019 |   85 k |     0.42 |  0.008 | 200.9 k | 
| LEA47  | 1M      | 8310-5  | 2020 |  273 k |     0.45 |  0.001 | 601.3 k |
| LEA47  | 1M      | 55758-7 | 2013 |  549 k |    18.38 |  2.954 |  29.9 k | 
| LEA47  | 1M      | 55758-7 | 2019 |  1.0 M |    16.52 |  0.112 |  63.2 k | 
| LEA47  | 1M      | 55758-7 | 2020 |  1.1 M |    16.74 |  0.238 |  63.9 k |
| LEA47  | 1M      | 72514-3 | 2013 |  1.4 M |   220.21 | 38.049 |   6.6 k | 
| LEA47  | 1M      | 72514-3 | 2019 |  2.8 M |   278.23 | 10.056 |  10.2 k | 
| LEA47  | 1M      | 72514-3 | 2020 |  2.9 M |   274.85 | 20.144 |  10.7 k |
| LEA58  | 1M      | 8310-5  | 2013 |   50 k |     0.41 |  0.009 | 121.7 k | 
| LEA58  | 1M      | 8310-5  | 2019 |   85 k |     0.41 |  0.008 | 208.2 k | 
| LEA58  | 1M      | 8310-5  | 2020 |  273 k |     0.41 |  0.003 | 662.0 k |
| LEA58  | 1M      | 55758-7 | 2013 |  549 k |     9.45 |  0.038 |  58.0 k | 
| LEA58  | 1M      | 55758-7 | 2019 |  1.0 M |     9.68 |  0.025 | 107.8 k | 
| LEA58  | 1M      | 55758-7 | 2020 |  1.1 M |     9.57 |  0.020 | 111.8 k |
| LEA58  | 1M      | 72514-3 | 2013 |  1.4 M |    16.96 |  0.180 |  85.1 k | 
| LEA58  | 1M      | 72514-3 | 2019 |  2.8 M |    17.01 |  0.076 | 167.1 k | 
| LEA58  | 1M      | 72514-3 | 2020 |  2.9 M |    17.29 |  0.085 | 170.2 k |
| A5N46  | 1M      | 8310-5  | 2013 |   50 k |     0.26 |  0.002 | 197.8 k | 
| A5N46  | 1M      | 8310-5  | 2019 |   85 k |     0.26 |  0.012 | 327.7 k | 
| A5N46  | 1M      | 8310-5  | 2020 |  273 k |     0.27 |  0.017 | 995.4 k |
| A5N46  | 1M      | 55758-7 | 2013 |  549 k |    38.47 |  0.031 |  14.3 k | 
| A5N46  | 1M      | 55758-7 | 2019 |  1.0 M |    39.94 |  0.008 |  26.1 k | 
| A5N46  | 1M      | 55758-7 | 2020 |  1.1 M |    39.96 |  0.024 |  26.8 k |
| A5N46  | 1M      | 72514-3 | 2013 |  1.4 M |    52.83 |  0.559 |  27.3 k | 
| A5N46  | 1M      | 72514-3 | 2019 |  2.8 M |    51.90 |  0.215 |  54.8 k | 
| A5N46  | 1M      | 72514-3 | 2020 |  2.9 M |    51.16 |  0.062 |  57.5 k |

¹ resources per second

### Downloading Resources

| System | Dataset | Code    | Date | # Hits | Time (s) |  StdDev | Res/s ¹ |
|--------|---------|---------|-----:|-------:|---------:|--------:|--------:|
| LEA47  | 1M      | 8310-5  | 2013 |   50 k |     5.93 |   0.016 |   8.5 k |
| LEA47  | 1M      | 8310-5  | 2019 |   85 k |     6.60 |   0.025 |  12.9 k |
| LEA47  | 1M      | 8310-5  | 2020 |  273 k |     9.89 |   0.062 |  27.6 k |
| LEA47  | 1M      | 55758-7 | 2013 |  549 k |   253.20 |   1.566 |   2.2 k |
| LEA47  | 1M      | 55758-7 | 2019 |  1.0 M |   470.48 | 266.452 |   2.2 k |
| LEA47  | 1M      | 55758-7 | 2020 |  1.1 M |   274.58 |  10.132 |   3.9 k |
| LEA47  | 1M      | 72514-3 | 2013 |  1.4 M |  3107.67 | 498.287 |     464 |
| LEA47  | 1M      | 72514-3 | 2019 |  2.8 M |  4180.95 | 150.134 |     680 |
| LEA47  | 1M      | 72514-3 | 2020 |  2.9 M |  4316.09 |   9.551 |     682 |
| LEA58  | 1M      | 8310-5  | 2013 |   50 k |     6.06 |   0.025 |   8.3 k |
| LEA58  | 1M      | 8310-5  | 2019 |   85 k |     6.75 |   0.057 |  12.6 k |
| LEA58  | 1M      | 8310-5  | 2020 |  273 k |    10.03 |   0.065 |  27.2 k |
| LEA58  | 1M      | 55758-7 | 2013 |  549 k |   251.57 |   0.633 |   2.2 k |
| LEA58  | 1M      | 55758-7 | 2019 |  1.0 M |   264.94 |   1.160 |   3.9 k |
| LEA58  | 1M      | 55758-7 | 2020 |  1.1 M |   265.40 |   2.129 |   4.0 k |
| LEA58  | 1M      | 72514-3 | 2013 |  1.4 M |   468.79 |   3.568 |   3.1 k |
| LEA58  | 1M      | 72514-3 | 2019 |  2.8 M |   505.10 |   8.743 |   5.6 k |
| LEA58  | 1M      | 72514-3 | 2020 |  2.9 M |   504.28 |   4.828 |   5.8 k |
| A5N46  | 1M      | 8310-5  | 2013 |   50 k |     3.16 |   0.005 |  15.9 k |
| A5N46  | 1M      | 8310-5  | 2019 |   85 k |     3.52 |   0.008 |  24.2 k |
| A5N46  | 1M      | 8310-5  | 2020 |  273 k |     5.30 |   0.021 |  51.6 k |
| A5N46  | 1M      | 55758-7 | 2013 |  549 k |   458.76 |   0.421 |   1.2 k |
| A5N46  | 1M      | 55758-7 | 2019 |  1.0 M |   497.68 |   0.049 |   2.1 k |
| A5N46  | 1M      | 55758-7 | 2020 |  1.1 M |   502.84 |   3.870 |   2.1 k |
| A5N46  | 1M      | 72514-3 | 2013 |  1.4 M |   619.29 |   3.956 |   2.3 k |
| A5N46  | 1M      | 72514-3 | 2019 |  2.8 M |   711.36 |   7.314 |   4.0 k |
| A5N46  | 1M      | 72514-3 | 2020 |  2.9 M |   708.03 |   0.125 |   4.2 k |

¹ resources per second

## Category and Date Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific category and date.

### Script

The script `category-date-search.sh` is used.

### Counting

| System | Dataset | Code        | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|-----:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | laboratory  | 2013 | 19.6 M |   194.72 |  1.585 | 100.8 k | 
| LEA47  | 1M      | laboratory  | 2019 | 38.4 M |   204.33 |  0.532 | 187.9 k | 
| LEA47  | 1M      | laboratory  | 2020 | 44.9 M |   205.06 |  0.565 | 219.0 k |
| LEA47  | 1M      | vital-signs | 2013 |  7.6 M |   326.83 |  6.978 |  23.2 k | 
| LEA47  | 1M      | vital-signs | 2019 | 14.3 M |   338.42 |  0.157 |  42.3 k | 
| LEA47  | 1M      | vital-signs | 2020 | 15.8 M |   334.29 |  4.415 |  47.2 k |
| LEA58  | 1M      | laboratory  | 2013 | 19.6 M |   118.38 |  0.677 | 165.8 k | 
| LEA58  | 1M      | laboratory  | 2019 | 38.4 M |   117.87 |  0.465 | 325.8 k | 
| LEA58  | 1M      | laboratory  | 2020 | 44.9 M |   140.11 | 19.633 | 320.5 k |
| LEA58  | 1M      | vital-signs | 2013 |  7.6 M |    43.99 |  0.135 | 172.8 k | 
| LEA58  | 1M      | vital-signs | 2019 | 14.3 M |    45.09 |  0.629 | 317.8 k | 
| LEA58  | 1M      | vital-signs | 2020 | 15.8 M |    44.77 |  0.547 | 352.8 k |
| A5N46  | 1M      | laboratory  | 2013 | 19.6 M |    91.70 |  0.219 | 214.1 k | 
| A5N46  | 1M      | laboratory  | 2019 | 38.4 M |    92.90 |  0.116 | 413.3 k | 
| A5N46  | 1M      | laboratory  | 2020 | 44.9 M |    93.40 |  0.051 | 480.8 k |
| A5N46  | 1M      | vital-signs | 2013 |  7.6 M |    65.65 |  0.696 | 115.8 k | 
| A5N46  | 1M      | vital-signs | 2019 | 14.3 M |    66.52 |  0.791 | 215.4 k | 
| A5N46  | 1M      | vital-signs | 2020 | 15.8 M |    66.34 |  0.130 | 238.1 k |

## Code and Patient Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code for 1000 patients.

### Script

The script `code-patient-search.sh` is used.

### Counting

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.03 |  0.002 |  35.7 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.10 |  0.003 | 288.1 k |
| LEA47  | 1M      | 72514-3 |  113 k |     0.24 |  0.002 | 478.7 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.03 |  0.002 |  34.4 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.10 |  0.001 | 275.4 k |
| LEA58  | 1M      | 72514-3 |  113 k |     0.29 |  0.010 | 392.0 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.001 |  74.5 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.05 |  0.001 | 574.8 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.12 |  0.001 | 930.9 k |

¹ resources per second

### Downloading Resources

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.07 |  0.000 |  13.5 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.63 |  0.014 |  44.6 k |
| LEA47  | 1M      | 72514-3 |  113 k |     2.33 |  0.024 |  48.5 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.06 |  0.005 |  14.9 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.68 |  0.005 |  41.6 k |
| LEA58  | 1M      | 72514-3 |  113 k |     2.16 |  0.065 |  52.4 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.03 |  0.000 |  31.5 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.33 |  0.009 |  86.2 k |
| A5N46  | 1M      | 72514-3 |  113 k |     1.15 |  0.014 |  98.4 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.04 |  0.000 |  23.6 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.48 |  0.012 |  59.0 k |
| LEA47  | 1M      | 72514-3 |  113 k |     1.63 |  0.000 |  69.4 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.04 |  0.000 |  23.6 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.48 |  0.016 |  58.6 k |
| LEA58  | 1M      | 72514-3 |  113 k |     1.53 |  0.043 |  73.9 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.02 |  0.000 |  47.2 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.24 |  0.005 | 115.7 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.82 |  0.008 | 138.0 k |

¹ resources per second

## Multiple Codes and Patient Search

This section evaluates the performance of FHIR Search for selecting Observation and Condition resources with multiple codes for 1000 patients.

The following codes were used:

* 10 LOINC codes from `observation-codes-10.txt`
* 100 LOINC codes from `observation-codes-100.txt`
* 1k SNOMED CT codes from `condition-codes-disease-1k.txt`
* 10k SNOMED CT codes from `condition-codes-disease-10k.txt`

### Script

The script `multiple-codes-patient-search.sh` is used.

### Counting

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |    10 |  121 k |     0.30 |  0.004 | 408.8 k |
| LEA47  | 1M      |   100 |  1.1 M |     2.33 |  0.005 | 470.1 k |
| LEA47  | 1M      |    1k |    247 |     2.80 |  0.006 |      88 |
| LEA47  | 1M      |   10k |    1 k |    39.80 |  0.146 |      32 |
| LEA58  | 1M      |    10 |  121 k |     0.31 |  0.011 | 396.5 k |
| LEA58  | 1M      |   100 |  1.1 M |     2.47 |  0.022 | 443.8 k |
| LEA58  | 1M      |    1k |    247 |     2.89 |  0.011 |      85 |
| LEA58  | 1M      |   10k |    1 k |    41.13 |  0.439 |      31 |
| A5N46  | 1M      |    10 |  121 k |     0.15 |  0.001 | 799.0 k |
| A5N46  | 1M      |   100 |  1.1 M |     1.20 |  0.004 | 915.4 k |
| A5N46  | 1M      |    1k |    247 |     1.35 |  0.007 |     183 |
| A5N46  | 1M      |   10k |    1 k |    20.29 |  0.189 |      62 |

¹ resources per second

### Downloading Resources

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |    10 |  121 k |     2.44 |  0.054 |  49.7 k |
| LEA47  | 1M      |   100 |  1.1 M |    21.70 |  0.310 |  50.5 k |
| LEA47  | 1M      |    1k |    247 |     2.81 |  0.016 |      88 |
| LEA47  | 1M      |   10k |    1 k |    40.48 |  0.237 |      31 |
| LEA58  | 1M      |    10 |  121 k |     2.30 |  0.106 |  52.7 k |
| LEA58  | 1M      |   100 |  1.1 M |    20.19 |  0.281 |  54.3 k |
| LEA58  | 1M      |    1k |    247 |     2.88 |  0.042 |      86 |
| LEA58  | 1M      |   10k |    1 k |    40.97 |  0.233 |      31 |
| A5N46  | 1M      |    10 |  121 k |     1.21 |  0.012 | 100.0 k |
| A5N46  | 1M      |   100 |  1.1 M |    10.32 |  0.057 | 106.4 k |
| A5N46  | 1M      |    1k |    247 |     1.34 |  0.005 |     185 |
| A5N46  | 1M      |   10k |    1 k |    20.32 |  0.139 |      62 |

¹ resources per second

## Code, Date, and Patient Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code, a specific date, and for 1000 patients.

### Script

The script `code-date-patient-search.sh` is used.

### Counting

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.04 |  0.006 |   6.9 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.15 |  0.001 |  18.2 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.43 |  0.009 |  27.9 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.000 |  18.8 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.08 |  0.001 |  36.8 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.22 |  0.004 |  53.5 k |

¹ resources per second

### Downloading Resources

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.04 |  0.000 |   6.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.24 |  0.005 |  11.6 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.65 |  0.017 |  18.3 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.02 |  0.000 |  12.3 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.14 |  0.008 |  20.2 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.36 |  0.005 |  32.9 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.03 |  0.000 |   8.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.19 |  0.005 |  14.6 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.61 |  0.016 |  19.6 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.000 |  24.6 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.12 |  0.005 |  24.2 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.32 |  0.000 |  37.4 k |

¹ resources per second

## Simple Date Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific effective year.

### Script

The script `simple-date-search.sh` is used.

### Counting

| System | Dataset | Year | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 2013 |  3.1 M |     2.12 |  0.023 |    0.67 |
| LEA36  | 100k    | 2019 |  6.0 M |     3.81 |  0.062 |    0.63 |
| LEA47  | 100k    | 2013 |  3.1 M |     2.13 |  0.068 |    0.68 |
| LEA47  | 100k    | 2019 |  6.0 M |     4.17 |  0.175 |    0.69 |
| LEA58  | 100k    | 2013 |  3.1 M |     2.22 |  0.071 |    0.71 |
| LEA58  | 100k    | 2019 |  6.0 M |     4.19 |  0.056 |    0.70 |
| LEA47  | 1M      | 2013 | 31.1 M |   108.65 |  0.558 | 286.0 k |
| LEA47  | 1M      | 2019 | 60.0 M |   209.27 |  1.670 | 287.0 k |
| LEA58  | 1M      | 2013 | 31.1 M |   107.81 |  1.260 | 288.2 k |
| LEA58  | 1M      | 2019 | 60.0 M |   210.81 |  0.364 | 284.9 k |
| A5N46  | 1M      | 2013 | 31.1 M |    56.17 |  0.079 | 553.2 k |
| A5N46  | 1M      | 2019 | 60.0 M |   108.41 |  0.964 | 554.0 k |

¹ resources per second

### Downloading Resources

| System | Dataset | Year | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 2013 |  3.1 M |    52.42 |  0.545 |   16.77 |
| LEA36  | 100k    | 2019 |  6.0 M |   129.84 |  5.393 |   21.71 |
| LEA47  | 100k    | 2013 |  3.1 M |    53.41 |  0.377 |   17.08 |
| LEA47  | 100k    | 2019 |  6.0 M |   107.15 |  0.116 |   17.92 |
| LEA58  | 100k    | 2013 |  3.1 M |    53.07 |  0.090 |   16.98 |
| LEA58  | 100k    | 2019 |  6.0 M |   100.73 |  0.473 |   16.84 |
| LEA47  | 1M      | 2013 | 31.1 M |   819.06 |  1.965 |  37.9 k |
| LEA47  | 1M      | 2019 | 60.0 M |  1634.48 | 16.647 |  36.7 k |
| LEA58  | 1M      | 2013 | 31.1 M |   596.05 | 18.812 |  52.1 k |
| LEA58  | 1M      | 2019 | 60.0 M |  1451.13 | 77.328 |  41.4 k |
| A5N46  | 1M      | 2013 | 31.1 M |   346.26 |  8.535 |  89.7 k |
| A5N46  | 1M      | 2019 | 60.0 M |   648.31 |  4.687 |  92.6 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Year | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 2013 |  3.1 M |    32.07 |  0.278 |   10.26 |
| LEA36  | 100k    | 2019 |  6.0 M |    79.92 |  4.179 |   13.36 |
| LEA47  | 100k    | 2013 |  3.1 M |    31.99 |  0.061 |   10.23 |
| LEA47  | 100k    | 2019 |  6.0 M |    66.33 |  0.045 |   11.09 |
| LEA58  | 100k    | 2013 |  3.1 M |    32.43 |  0.340 |   10.37 |
| LEA58  | 100k    | 2019 |  6.0 M |    62.14 |  0.488 |   10.39 |
| LEA47  | 1M      | 2013 | 31.1 M |   627.50 |  9.911 |  49.5 k |
| LEA47  | 1M      | 2019 | 60.0 M |  1282.05 |  4.997 |  46.8 k |
| LEA58  | 1M      | 2013 | 31.1 M |   439.48 |  1.237 |  70.7 k |
| LEA58  | 1M      | 2019 | 60.0 M |  1238.84 |  3.080 |  48.5 k |
| A5N46  | 1M      | 2013 | 31.1 M |   259.64 |  0.297 | 119.7 k |
| A5N46  | 1M      | 2019 | 60.0 M |   492.57 |  2.019 | 121.9 k |

¹ resources per second

## Patient Date Search

This section evaluates the performance of FHIR Search for selecting Patient resources with a specific birth date.

### Script

The script `patient-date-search.sh` is used.

### Counting

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA58  | 1M      | gt1998-04-10 |  227 k |     1.08 |  0.015 | 210.8 k |
| LEA58  | 1M      | ge1998-04-10 |  227 k |     1.16 |  0.023 | 196.1 k |
| LEA58  | 1M      | lt1998-04-10 |  773 k |     3.22 |  0.064 | 240.1 k |
| LEA58  | 1M      | le1998-04-10 |  773 k |     3.24 |  0.071 | 238.5 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     0.50 |  0.002 | 457.9 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     0.51 |  0.003 | 448.1 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     1.54 |  0.014 | 501.5 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     1.53 |  0.013 | 505.2 k |

¹ resources per second

### Downloading Resources

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA58  | 1M      | gt1998-04-10 |  227 k |     9.87 |  0.009 |  23.0 k |
| LEA58  | 1M      | ge1998-04-10 |  227 k |     9.86 |  0.052 |  23.0 k |
| LEA58  | 1M      | lt1998-04-10 |  773 k |    36.55 |  0.178 |  21.1 k |
| LEA58  | 1M      | le1998-04-10 |  773 k |    35.91 |  0.274 |  21.5 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     4.78 |  0.019 |  47.6 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     4.81 |  0.017 |  47.3 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |    17.73 |  0.009 |  43.6 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |    17.71 |  0.014 |  43.6 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=id` was used.

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA58  | 1M      | gt1998-04-10 |  227 k |     4.29 |  0.057 |  53.0 k |
| LEA58  | 1M      | ge1998-04-10 |  227 k |     4.18 |  0.082 |  54.4 k |
| LEA58  | 1M      | lt1998-04-10 |  773 k |    13.73 |  0.102 |  56.2 k |
| LEA58  | 1M      | le1998-04-10 |  773 k |    13.83 |  0.067 |  55.8 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     2.04 |  0.005 | 111.3 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     2.08 |  0.008 | 109.3 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     6.70 |  0.051 | 115.3 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     6.78 |  0.046 | 114.0 k |

¹ resources per second

## Used Dataset

The dataset was generated with Synthea v3.1.1. The resource generation process is described [here](synthea/README.md).

## Controlling and Monitoring the Caches

The size of the resource cache can be set with the `DB_RESOURCE_CACHE_SIZE_RATIO` environment variable, which specifies the ratio of JVM heap size that is allocated to the resource cache.

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
