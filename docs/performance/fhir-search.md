# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code at a rate of **9.5 million resources per second** and export the matching resources at **280,000 resources per second**. This performance is independent of the total number of resources held in the database.

## Systems

The following systems, with increasing resources, were used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM | SSD                           |
|--------|----------|-------------|------:|--------:|-------------------------------|
| LEA25  | on-prem  | EPYC 7543P  |     4 |  32 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA36  | on-prem  | EPYC 7543P  |     8 |  64 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA58  | on-prem  | EPYC 7543P  |    32 | 256 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 12.8 TB Huawei OceanDisk 300P |
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 4 TB Samsung 990 Pro          |

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
| LEA47  | 1M      | 8310-5  |  1.2 M |     0.42 |  0.010 |   2.8 M |
| LEA47  | 1M      | 55758-7 | 10.1 M |     3.85 |  0.068 |   2.6 M |
| LEA47  | 1M      | 72514-3 | 27.3 M |     8.96 |  0.098 |   3.0 M |
| LEA58  | 1M      | 8310-5  |  1.2 M |     0.39 |  0.009 |   3.0 M |
| LEA58  | 1M      | 55758-7 | 10.1 M |     2.84 |  0.026 |   3.6 M |
| LEA58  | 1M      | 72514-3 | 27.3 M |     7.52 |  0.138 |   3.6 M |
| LEA79  | 1M      | 8310-5  |  1.2 M |     0.16 |  0.015 |   7.5 M |
| LEA79  | 1M      | 55758-7 | 10.1 M |     1.27 |  0.083 |   8.0 M |
| LEA79  | 1M      | 72514-3 | 27.3 M |     3.39 |  0.127 |   8.1 M |
| A5N46  | 1M      | 8310-5  |  1.2 M |     0.12 |  0.009 |   9.5 M |
| A5N46  | 1M      | 55758-7 | 10.1 M |     1.19 |  0.049 |   8.5 M |
| A5N46  | 1M      | 72514-3 | 27.3 M |     3.87 |  0.082 |   7.1 M |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/simple-code-search-download-1M.txt"
  title="Simple Code Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="300"
  :series="['0.2 % hits', '2 % hits', '5 % hits']" />

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
| LEA47  | 1M      | 8310-5  |  1.2 M |    19.08 |  0.095 |  60.7 k |         
| LEA47  | 1M      | 55758-7 | 10.1 M |   296.37 | 46.454 |  34.2 k |         
| LEA47  | 1M      | 72514-3 | 27.3 M |   658.56 |  6.587 |  41.5 k |
| LEA58  | 1M      | 8310-5  |  1.2 M |    20.70 |  0.320 |  56.0 k |         
| LEA58  | 1M      | 55758-7 | 10.1 M |   171.60 |  4.241 |  59.1 k |         
| LEA58  | 1M      | 72514-3 | 27.3 M |   497.29 | 26.969 |  55.0 k |
| LEA79  | 1M      | 8310-5  |  1.2 M |     4.46 |  0.036 | 259.9 k |
| LEA79  | 1M      | 55758-7 | 10.1 M |    35.34 |  0.077 | 286.9 k |
| LEA79  | 1M      | 72514-3 | 27.3 M |    97.35 |  0.385 | 280.9 k |
| A5N46  | 1M      | 8310-5  |  1.2 M |     4.12 |  0.085 | 281.3 k |
| A5N46  | 1M      | 55758-7 | 10.1 M |    42.37 |  0.938 | 239.3 k |
| A5N46  | 1M      | 72514-3 | 27.3 M |   180.86 |  0.823 | 151.2 k |

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
| LEA47  | 1M      | 8310-5  |  1.2 M |    13.67 |  0.204 |   84.8 k |          
| LEA47  | 1M      | 55758-7 | 10.1 M |   338.50 |  7.638 |   30.0 k |          
| LEA47  | 1M      | 72514-3 | 27.3 M |   546.44 |  7.829 |   50.0 k |
| LEA58  | 1M      | 8310-5  |  1.2 M |    15.42 |  0.208 |   75.1 k |          
| LEA58  | 1M      | 55758-7 | 10.1 M |   124.03 |  1.024 |   81.8 k |          
| LEA58  | 1M      | 72514-3 | 27.3 M |   343.16 |  1.412 |   79.7 k |
| LEA79  | 1M      | 8310-5  |  1.2 M |     3.18 |  0.037 |  364.9 k |
| LEA79  | 1M      | 55758-7 | 10.1 M |    25.65 |  0.059 |  395.4 k |
| LEA79  | 1M      | 72514-3 | 27.3 M |    73.66 |  0.039 |  371.2 k |
| A5N46  | 1M      | 8310-5  |  1.2 M |     2.90 |  0.029 |  399.7 k |
| A5N46  | 1M      | 55758-7 | 10.1 M |    39.04 |  0.116 |  259.7 k |
| A5N46  | 1M      | 72514-3 | 27.3 M |   145.83 |  0.135 |  187.5 k |

¹ resources per second

## Multiple Codes Search

This section evaluates the performance of FHIR Search for selecting Observation resources with multiple codes.

The following codes were used:

* 10 LOINC codes from `observation-codes-10.txt`
* 100 LOINC codes from `observation-codes-100.txt`

### Script

The script `multiple-codes-search.sh` is used.

### Counting

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 10    | 29.1 M |    12.69 |  0.147 |   2.3 M |
| LEA47  | 1M      | 100   | 36.3 M |    19.32 |  0.067 |   1.9 M |
| LEA79  | 1M      | 10    | 29.1 M |     6.41 |  0.026 |   4.5 M |
| LEA79  | 1M      | 100   | 36.3 M |     9.86 |  0.044 |   3.7 M |
| A5N46  | 1M      | 10    | 29.1 M |     4.58 |  0.046 |   6.4 M |
| A5N46  | 1M      | 100   | 36.3 M |     7.61 |  0.157 |   4.8 M |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/multiple-codes-search-download-1M.txt"
  title="Multiple Codes Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="230"
  :series="['10 codes', '100 codes']" />

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 10    | 29.1 M |   680.86 |  9.238 |  42.7 k |
| LEA47  | 1M      | 100   | 36.3 M |  1663.36 |  1.855 |  21.8 k |
| LEA79  | 1M      | 10    | 29.1 M |   162.51 |  1.068 | 178.9 k |
| LEA79  | 1M      | 100   | 36.3 M |   276.96 |  0.693 | 131.2 k |
| A5N46  | 1M      | 10    | 29.1 M |   187.18 |  0.314 | 155.3 k |
| A5N46  | 1M      | 100   | 36.3 M |   286.61 |  0.507 | 126.8 k |

¹ resources per second

## Multiple Codes Search – ValueSet

This section evaluates the performance of FHIR Search for selecting Observation resources with multiple codes using the in-modifier with a VCL value set URL.

The following codes were used:

* 10 LOINC codes from `observation-codes-10.txt`
* 100 LOINC codes from `observation-codes-100.txt`

### Script

The script `multiple-codes-search-vs.sh` is used.

### Counting

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | 10    | 29.1 M |     7.68 |  0.102 |   3.8 M |
| A5N46  | 1M      | 100   | 36.3 M |    11.07 |  0.206 |   3.3 M |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/multiple-codes-search-vs-download-1M.txt"
  title="Multiple Codes Search – ValueSet - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="230"
  :series="['10 codes', '100 codes']" />

| System | Dataset | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | 10    | 29.1 M |   181.58 |  0.086 | 160.1 k |
| A5N46  | 1M      | 100   | 36.3 M |   289.41 |  0.964 | 125.5 k |

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
| LEA79  | 1M      | laboratory  | top-5 | 90.3 M |   192.58 |  0.744 | 468.7 k |
| LEA79  | 1M      | vital-signs | low-5 |  3.5 M |     0.78 |  0.003 |   4.5 M |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |   209.64 |  0.462 | 430.6 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |     2.68 |  0.042 |   1.3 M |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/multiple-search-param-search-download-1M.txt"
  title="Multiple Search Param Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="160"
  :series="['laboratory', 'vital-signs']" />

| System | Dataset | Category    | Codes | # Hits | Time (s) |  StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|--------:|--------:|
| LEA47  | 1M      | laboratory  | top-5 | 90.3 M |  2923.45 |   7.951 |  30.9 k |
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |   153.07 |  29.422 |  23.0 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |  2490.08 | 101.868 |  36.2 k |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |    62.43 |   0.356 |  56.5 k |
| LEA79  | 1M      | laboratory  | top-5 | 90.3 M |   729.98 |   0.403 | 123.7 k |
| LEA79  | 1M      | vital-signs | low-5 |  3.5 M |    31.78 |   0.212 | 110.9 k |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |   598.44 |   3.996 | 150.8 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |    45.85 |   0.840 |  76.9 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Category    | Codes | # Hits | Time (s) |  StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|--------:|--------:|
| LEA47  | 1M      | laboratory  | top-5 | 90.3 M |  2625.21 |  24.973 |  34.4 k |
| LEA47  | 1M      | vital-signs | low-5 |  3.5 M |   118.72 |   0.099 |  29.7 k |
| LEA58  | 1M      | laboratory  | top-5 | 90.3 M |  2200.60 | 232.968 |  41.0 k |
| LEA58  | 1M      | vital-signs | low-5 |  3.5 M |    54.94 |   5.827 |  64.2 k |
| LEA79  | 1M      | laboratory  | top-5 | 90.3 M |   715.55 |  12.516 | 126.2 k |
| LEA79  | 1M      | vital-signs | low-5 |  3.5 M |    31.15 |   0.567 | 113.1 k |
| A5N46  | 1M      | laboratory  | top-5 | 90.3 M |   586.63 |  10.563 | 153.9 k |
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |    44.97 |   0.942 |  78.4 k |

¹ resources per second

## Forward Chaining Search

This section evaluates the performance of FHIR search queries with one forward chaining search parameter.

### Script

The script `forward-chaining-search.sh` is used.

### Counting

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |   13 k |     0.81 |  0.004 |  16.0 k |
| A5N46  | 1M      |   13 k |     0.16 |  0.001 |  79.7 k |

¹ resources per second

### Downloading Resources

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |   13 k |     8.15 |  0.029 |   1.6 k |
| A5N46  | 1M      |   13 k |     1.64 |  0.005 |   8.0 k |

¹ resources per second

## Token and Forward Chaining Search

This section evaluates the performance of FHIR search queries with one badly discriminating token search parameter and one well discriminating forward chaining search parameter.

### Script

The script `token-forward-chaining-search.sh` is used.

### Counting

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |   32 k |     0.30 |  0.010 | 105.2 k |
| LEA79  | 1M      |   32 k |     0.13 |  0.003 | 236.1 k |
| A5N46  | 1M      |   32 k |     0.09 |  0.002 | 359.2 k |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/token-forward-chaining-search-download-1M.txt"
  title="Token and Forward Chaining Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="40"
  :series="['32 k hits']" />

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      |   32 k |     3.95 |  0.042 |   8.0 k |
| LEA79  | 1M      |   32 k |     1.51 |  0.012 |  21.0 k |
| A5N46  | 1M      |   32 k |     0.95 |  0.005 |  33.4 k |

¹ resources per second

## Code and Value Search

This section evaluates the performance of FHIR Search for selecting Observation resources with a specific code and value.

### Script

The script `code-value-search.sh` is used.

### Counting

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|--------:|
| LEA79  | 1M      | 29463-7 |  26.8 |  1.6 M |     4.57 |  0.083 | 341.8 k |
| LEA79  | 1M      | 29463-7 |  79.5 |  7.8 M |     4.56 |  0.042 |   1.7 M |
| LEA79  | 1M      | 29463-7 |   183 | 15.9 M |     4.72 |  0.108 |   3.4 M |
| A5N46  | 1M      | 29463-7 |  26.8 |  1.6 M |    51.69 |  0.065 |  30.2 k |
| A5N46  | 1M      | 29463-7 |  79.5 |  7.8 M |    53.30 |  0.057 | 147.3 k |
| A5N46  | 1M      | 29463-7 |   183 | 15.9 M |    56.35 |  0.077 | 282.3 k |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/code-value-search-download-1M.txt"
  title="Code and Value Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="60"
  :series="['value 26.8', 'value 79.5', 'value 183']" />

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev |  Res/s ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|---------:|
| LEA79  | 1M      | 29463-7 |  26.8 |  1.6 M |   213.45 |  0.157 |    7.3 k |
| LEA79  | 1M      | 29463-7 |  79.5 |  7.8 M |   249.59 |  0.036 |   31.5 k |
| LEA79  | 1M      | 29463-7 |   183 | 15.9 M |   313.82 | 13.121 |   50.7 k |
| A5N46  | 1M      | 29463-7 |  26.8 |  1.6 M |   647.99 |  0.565 |    2.4 k |
| A5N46  | 1M      | 29463-7 |  79.5 |  7.8 M |   813.16 |  3.758 |    9.6 k |
| A5N46  | 1M      | 29463-7 |   183 | 15.9 M |   974.11 | 10.472 |   16.3 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Code    | Value | # Hits | Time (s) | StdDev |  Res/s ¹ |
|--------|---------|---------|------:|-------:|---------:|-------:|---------:|
| LEA79  | 1M      | 29463-7 |  26.8 |  1.6 M |   212.88 |  0.033 |    7.3 k |
| LEA79  | 1M      | 29463-7 |  79.5 |  7.8 M |   247.65 |  0.311 |   31.7 k |
| LEA79  | 1M      | 29463-7 |   183 | 15.9 M |   288.99 |  0.074 |   55.0 k |
| A5N46  | 1M      | 29463-7 |  26.8 |  1.6 M |   647.67 |  0.611 |    2.4 k |
| A5N46  | 1M      | 29463-7 |  79.5 |  7.8 M |   809.25 |  0.257 |    9.7 k |
| A5N46  | 1M      | 29463-7 |   183 | 15.9 M |   963.35 |  0.187 |   16.5 k |

¹ resources per second

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
| LEA79  | 1M      | 8310-5  | 2013 |   50 k |     0.22 |  0.016 | 226.6 k | 
| LEA79  | 1M      | 8310-5  | 2019 |   85 k |     0.22 |  0.011 | 385.9 k | 
| LEA79  | 1M      | 8310-5  | 2020 |  273 k |     0.22 |  0.007 |   1.2 M |
| LEA79  | 1M      | 55758-7 | 2013 |  549 k |     3.13 |  0.069 | 175.5 k | 
| LEA79  | 1M      | 55758-7 | 2019 |  1.0 M |     3.04 |  0.046 | 342.7 k | 
| LEA79  | 1M      | 55758-7 | 2020 |  1.1 M |     3.39 |  0.115 | 315.1 k |
| LEA79  | 1M      | 72514-3 | 2013 |  1.4 M |     5.64 |  0.313 | 256.1 k | 
| LEA79  | 1M      | 72514-3 | 2019 |  2.8 M |     6.01 |  0.249 | 472.7 k | 
| LEA79  | 1M      | 72514-3 | 2020 |  2.9 M |     5.87 |  0.165 | 501.1 k |
| A5N46  | 1M      | 8310-5  | 2013 |   50 k |     0.27 |  0.010 | 187.5 k | 
| A5N46  | 1M      | 8310-5  | 2019 |   85 k |     0.26 |  0.004 | 326.2 k | 
| A5N46  | 1M      | 8310-5  | 2020 |  273 k |     0.28 |  0.010 | 973.4 k |
| A5N46  | 1M      | 55758-7 | 2013 |  549 k |    41.09 |  0.009 |  13.4 k | 
| A5N46  | 1M      | 55758-7 | 2019 |  1.0 M |    42.03 |  0.021 |  24.8 k | 
| A5N46  | 1M      | 55758-7 | 2020 |  1.1 M |    42.01 |  0.048 |  25.5 k |
| A5N46  | 1M      | 72514-3 | 2013 |  1.4 M |    54.66 |  0.722 |  26.4 k | 
| A5N46  | 1M      | 72514-3 | 2019 |  2.8 M |    52.72 |  0.222 |  53.9 k | 
| A5N46  | 1M      | 72514-3 | 2020 |  2.9 M |    51.79 |  0.063 |  56.8 k |

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
| LEA79  | 1M      | 8310-5  | 2013 |   50 k |     3.81 |   0.029 |  13.2 k |
| LEA79  | 1M      | 8310-5  | 2019 |   85 k |     4.00 |   0.009 |  21.3 k |
| LEA79  | 1M      | 8310-5  | 2020 |  273 k |     5.19 |   0.026 |  52.7 k |
| LEA79  | 1M      | 55758-7 | 2013 |  549 k |   148.60 |   3.782 |   3.7 k |
| LEA79  | 1M      | 55758-7 | 2019 |  1.0 M |   148.40 |   0.083 |   7.0 k |
| LEA79  | 1M      | 55758-7 | 2020 |  1.1 M |   148.41 |   0.098 |   7.2 k |
| LEA79  | 1M      | 72514-3 | 2013 |  1.4 M |   270.37 |   0.200 |   5.3 k |
| LEA79  | 1M      | 72514-3 | 2019 |  2.8 M |   282.65 |   0.098 |  10.1 k |
| LEA79  | 1M      | 72514-3 | 2020 |  2.9 M |   287.54 |   3.771 |  10.2 k |
| A5N46  | 1M      | 8310-5  | 2013 |   50 k |     2.97 |   0.012 |  17.0 k |
| A5N46  | 1M      | 8310-5  | 2019 |   85 k |     3.16 |   0.009 |  26.9 k |
| A5N46  | 1M      | 8310-5  | 2020 |  273 k |     4.08 |   0.005 |  67.0 k |
| A5N46  | 1M      | 55758-7 | 2013 |  549 k |   371.68 |   0.400 |   1.5 k |
| A5N46  | 1M      | 55758-7 | 2019 |  1.0 M |   395.93 |   0.145 |   2.6 k |
| A5N46  | 1M      | 55758-7 | 2020 |  1.1 M |   397.53 |   0.379 |   2.7 k |
| A5N46  | 1M      | 72514-3 | 2013 |  1.4 M |   665.47 |   5.894 |   2.2 k |
| A5N46  | 1M      | 72514-3 | 2019 |  2.8 M |   759.56 |   0.604 |   3.7 k |
| A5N46  | 1M      | 72514-3 | 2020 |  2.9 M |   766.62 |   3.889 |   3.8 k |

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
| LEA79  | 1M      | laboratory  | 2013 | 19.6 M |    64.08 |  0.351 | 306.4 k | 
| LEA79  | 1M      | laboratory  | 2019 | 38.4 M |    63.92 |  0.269 | 600.8 k | 
| LEA79  | 1M      | laboratory  | 2020 | 44.9 M |    64.03 |  0.246 | 701.3 k |
| LEA79  | 1M      | vital-signs | 2013 |  7.6 M |    23.80 |  0.106 | 319.4 k | 
| LEA79  | 1M      | vital-signs | 2019 | 14.3 M |    23.63 |  0.134 | 606.5 k | 
| LEA79  | 1M      | vital-signs | 2020 | 15.8 M |    23.61 |  0.089 | 669.0 k |
| A5N46  | 1M      | laboratory  | 2013 | 19.6 M |    93.18 |  0.548 | 210.7 k | 
| A5N46  | 1M      | laboratory  | 2019 | 38.4 M |    99.43 |  5.372 | 386.2 k | 
| A5N46  | 1M      | laboratory  | 2020 | 44.9 M |    99.35 |  5.725 | 451.9 k |
| A5N46  | 1M      | vital-signs | 2013 |  7.6 M |    64.79 |  1.780 | 117.3 k | 
| A5N46  | 1M      | vital-signs | 2019 | 14.3 M |    66.91 |  0.802 | 214.2 k | 
| A5N46  | 1M      | vital-signs | 2020 | 15.8 M |    66.49 |  0.988 | 237.5 k |

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
| LEA79  | 1M      | 8310-5  |    943 |     0.02 |  0.002 |  59.3 k |
| LEA79  | 1M      | 55758-7 |   28 k |     0.06 |  0.002 | 442.0 k |
| LEA79  | 1M      | 72514-3 |  113 k |     0.16 |  0.000 | 704.4 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.001 |  99.5 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.02 |  0.004 |   1.5 M |
| A5N46  | 1M      | 72514-3 |  113 k |     0.04 |  0.007 |   2.9 M |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/code-patient-search-download-1M.txt"
  title="Code and Patient Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="250"
  :series="['8310-5', '55758-7', '72514-3']" />

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    943 |     0.07 |  0.000 |  13.5 k |
| LEA47  | 1M      | 55758-7 |   28 k |     0.63 |  0.014 |  44.6 k |
| LEA47  | 1M      | 72514-3 |  113 k |     2.33 |  0.024 |  48.5 k |
| LEA58  | 1M      | 8310-5  |    943 |     0.06 |  0.005 |  14.9 k |
| LEA58  | 1M      | 55758-7 |   28 k |     0.68 |  0.005 |  41.6 k |
| LEA58  | 1M      | 72514-3 |  113 k |     2.16 |  0.065 |  52.4 k |
| LEA79  | 1M      | 8310-5  |    943 |     0.03 |  0.000 |  31.4 k |
| LEA79  | 1M      | 55758-7 |   28 k |     0.26 |  0.012 | 106.7 k |
| LEA79  | 1M      | 72514-3 |  113 k |     0.75 |  0.005 | 151.4 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.03 |  0.002 |  33.7 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.15 |  0.012 | 182.5 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.46 |  0.012 | 245.4 k |

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
| LEA79  | 1M      | 8310-5  |    943 |     0.02 |  0.000 |  47.2 k |
| LEA79  | 1M      | 55758-7 |   28 k |     0.24 |  0.000 | 117.1 k |
| LEA79  | 1M      | 72514-3 |  113 k |     0.69 |  0.008 | 163.9 k |
| A5N46  | 1M      | 8310-5  |    944 |     0.02 |  0.003 |  55.5 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.12 |  0.003 | 227.7 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.38 |  0.006 | 298.3 k |

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
| LEA79  | 1M      |    10 |  121 k |     0.20 |  0.002 | 596.7 k |
| LEA79  | 1M      |   100 |  109 k |     0.38 |  0.002 | 288.8 k |
| LEA79  | 1M      |    1k |    247 |     1.72 |  0.017 |     143 |
| LEA79  | 1M      |   10k |    1 k |    28.11 |  0.250 |      45 |
| A5N46  | 1M      |    10 |  121 k |     0.07 |  0.008 |   1.7 M |
| A5N46  | 1M      |   100 |  109 k |     0.21 |  0.003 | 508.3 k |
| A5N46  | 1M      |    1k |    247 |     1.39 |  0.005 |     178 |
| A5N46  | 1M      |   10k |    1 k |    22.20 |  0.093 |      57 |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/multiple-codes-patient-search-download-1M.txt"
  title="Multiple Codes and Patient Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="250"
  :series="['10 codes', '100 codes']" />

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
| LEA79  | 1M      |    10 |  121 k |     0.91 |  0.022 | 133.1 k |
| LEA79  | 1M      |   100 |  109 k |     1.13 |  0.005 |  96.0 k |
| LEA79  | 1M      |    1k |    247 |     1.72 |  0.012 |     144 |
| LEA79  | 1M      |   10k |    1 k |    28.30 |  0.075 |      45 |
| A5N46  | 1M      |    10 |  121 k |     0.51 |  0.014 | 240.0 k |
| A5N46  | 1M      |   100 |  109 k |     0.83 |  0.017 | 131.7 k |
| A5N46  | 1M      |    1k |    247 |     1.39 |  0.005 |     177 |
| A5N46  | 1M      |   10k |    1 k |    22.20 |  0.031 |      57 |

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
| LEA79  | 1M      | 8310-5  |    246 |     0.02 |  0.002 |  12.4 k |
| LEA79  | 1M      | 55758-7 |    3 k |     0.10 |  0.002 |  27.2 k |
| LEA79  | 1M      | 72514-3 |   12 k |     0.29 |  0.004 |  41.2 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.001 |  17.0 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.08 |  0.001 |  35.9 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.21 |  0.003 |  57.3 k |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/code-date-patient-search-download-1M.txt"
  title="Code, Date, and Patient Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="50"
  :series="['8310-5', '55758-7', '72514-3']" />

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.04 |  0.000 |   6.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.24 |  0.005 |  11.6 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.65 |  0.017 |  18.3 k |
| LEA79  | 1M      | 8310-5  |    246 |     0.02 |  0.000 |  12.3 k |
| LEA79  | 1M      | 55758-7 |    3 k |     0.14 |  0.012 |  19.7 k |
| LEA79  | 1M      | 72514-3 |   12 k |     0.37 |  0.008 |  32.3 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.02 |  0.001 |  10.2 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.12 |  0.007 |  23.6 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.29 |  0.004 |  40.9 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=subject` was used.

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.03 |  0.000 |   8.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.19 |  0.005 |  14.6 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.61 |  0.016 |  19.6 k |
| LEA79  | 1M      | 8310-5  |    246 |     0.01 |  0.005 |  18.5 k |
| LEA79  | 1M      | 55758-7 |    3 k |     0.12 |  0.005 |  24.2 k |
| LEA79  | 1M      | 72514-3 |   12 k |     0.36 |  0.005 |  33.5 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.02 |  0.000 |  16.0 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.11 |  0.002 |  26.8 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.28 |  0.004 |  42.6 k |

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
| LEA47  | 1M      | 2019 | 60.1 M |   209.27 |  1.670 | 287.0 k |
| LEA58  | 1M      | 2013 | 31.1 M |   107.81 |  1.260 | 288.2 k |
| LEA58  | 1M      | 2019 | 60.1 M |   210.81 |  0.364 | 284.9 k |
| LEA79  | 1M      | 2013 | 31.1 M |    72.72 |  0.721 | 427.3 k |
| LEA79  | 1M      | 2019 | 60.1 M |   146.16 |  0.772 | 410.9 k |
| A5N46  | 1M      | 2013 | 31.1 M |    61.20 |  0.176 | 507.8 k |
| A5N46  | 1M      | 2019 | 60.1 M |   120.82 |  0.064 | 497.1 k |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/simple-date-search-download-1M.txt"
  title="Simple Date Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="200"
  :series="['2013', '2019']" />

| System | Dataset | Year | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 2013 |  3.1 M |    52.42 |  0.545 |   16.77 |
| LEA36  | 100k    | 2019 |  6.0 M |   129.84 |  5.393 |   21.71 |
| LEA47  | 100k    | 2013 |  3.1 M |    53.41 |  0.377 |   17.08 |
| LEA47  | 100k    | 2019 |  6.0 M |   107.15 |  0.116 |   17.92 |
| LEA58  | 100k    | 2013 |  3.1 M |    53.07 |  0.090 |   16.98 |
| LEA58  | 100k    | 2019 |  6.0 M |   100.73 |  0.473 |   16.84 |
| LEA47  | 1M      | 2013 | 31.1 M |   819.06 |  1.965 |  37.9 k |
| LEA47  | 1M      | 2019 | 60.1 M |  1634.48 | 16.647 |  36.7 k |
| LEA58  | 1M      | 2013 | 31.1 M |   596.05 | 18.812 |  52.1 k |
| LEA58  | 1M      | 2019 | 60.1 M |  1451.13 | 77.328 |  41.4 k |
| LEA79  | 1M      | 2013 | 31.1 M |   223.08 |  0.350 | 139.3 k |
| LEA79  | 1M      | 2019 | 60.1 M |   418.19 |  1.132 | 143.6 k |
| A5N46  | 1M      | 2013 | 31.1 M |   158.20 |  0.066 | 196.4 k |
| A5N46  | 1M      | 2019 | 60.1 M |   307.03 |  0.419 | 195.6 k |

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
| LEA47  | 1M      | 2019 | 60.1 M |  1282.05 |  4.997 |  46.8 k |
| LEA58  | 1M      | 2013 | 31.1 M |   439.48 |  1.237 |  70.7 k |
| LEA58  | 1M      | 2019 | 60.1 M |  1238.84 |  3.080 |  48.5 k |
| LEA79  | 1M      | 2013 | 31.1 M |   212.76 |  0.298 | 146.0 k |
| LEA79  | 1M      | 2019 | 60.1 M |   390.01 |  5.077 | 154.0 k |
| A5N46  | 1M      | 2013 | 31.1 M |   154.09 |  0.066 | 201.6 k |
| A5N46  | 1M      | 2019 | 60.1 M |   298.95 |  0.234 | 200.9 k |

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
| LEA79  | 1M      | gt1998-04-10 |  227 k |     0.69 |  0.008 | 329.4 k |
| LEA79  | 1M      | ge1998-04-10 |  227 k |     0.69 |  0.006 | 330.2 k |
| LEA79  | 1M      | lt1998-04-10 |  773 k |     2.16 |  0.035 | 357.9 k |
| LEA79  | 1M      | le1998-04-10 |  773 k |     2.18 |  0.014 | 353.8 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     0.24 |  0.006 | 943.4 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     0.24 |  0.017 | 949.7 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     0.64 |  0.023 |   1.2 M |
| A5N46  | 1M      | le1998-04-10 |  773 k |     0.65 |  0.019 |   1.2 M |

¹ resources per second

### Downloading Resources

<BarChart src="fhir-search/chart-data/patient-date-search-download-1M.txt"
  title="Patient Date Search - Download - Dataset 1M"
  x-label="System" :x-col="2"
  y-label="Resources/s" :y-col="4" :y-max="150"
  :series="['gt', 'ge', 'lt', 'le']" />

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA58  | 1M      | gt1998-04-10 |  227 k |     9.87 |  0.009 |  23.0 k |
| LEA58  | 1M      | ge1998-04-10 |  227 k |     9.86 |  0.052 |  23.0 k |
| LEA58  | 1M      | lt1998-04-10 |  773 k |    36.55 |  0.178 |  21.1 k |
| LEA58  | 1M      | le1998-04-10 |  773 k |    35.91 |  0.274 |  21.5 k |
| LEA79  | 1M      | gt1998-04-10 |  227 k |     2.42 |  0.037 |  94.1 k |
| LEA79  | 1M      | ge1998-04-10 |  227 k |     2.40 |  0.049 |  94.7 k |
| LEA79  | 1M      | lt1998-04-10 |  773 k |     8.18 |  0.012 |  94.4 k |
| LEA79  | 1M      | le1998-04-10 |  773 k |     8.08 |  0.036 |  95.6 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     1.63 |  0.027 | 139.9 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     1.65 |  0.020 | 137.8 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     5.50 |  0.019 | 140.5 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     5.40 |  0.010 | 143.2 k |

¹ resources per second

### Downloading Resources with Subsetting

If only a subset of a resource's information is needed, the `_elements` search parameter can be used to retrieve only specific properties. In this case, `_elements=id` was used.

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA58  | 1M      | gt1998-04-10 |  227 k |     4.29 |  0.057 |  53.0 k |
| LEA58  | 1M      | ge1998-04-10 |  227 k |     4.18 |  0.082 |  54.4 k |
| LEA58  | 1M      | lt1998-04-10 |  773 k |    13.73 |  0.102 |  56.2 k |
| LEA58  | 1M      | le1998-04-10 |  773 k |    13.83 |  0.067 |  55.8 k |
| LEA79  | 1M      | gt1998-04-10 |  227 k |     1.93 |  0.005 | 118.0 k |
| LEA79  | 1M      | ge1998-04-10 |  227 k |     1.91 |  0.005 | 118.8 k |
| LEA79  | 1M      | lt1998-04-10 |  773 k |     6.15 |  0.042 | 125.7 k |
| LEA79  | 1M      | le1998-04-10 |  773 k |     6.13 |  0.029 | 126.1 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     0.89 |  0.002 | 256.2 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     0.87 |  0.002 | 260.0 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     2.60 |  0.027 | 297.3 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     2.65 |  0.021 | 291.5 k |

¹ resources per second

## Used Dataset

The dataset was generated with Synthea v3.1.1. The resource generation process is described [here](synthea/README.md).

## Controlling and Monitoring the Caches

Blaze uses two caches to speed up FHIR Search. The resource cache holds the contents of resources and the resource handle cache holds the resource handles a search looks up for each of its hits.

The size of the resource cache can be set with the [`DB_RESOURCE_CACHE_SIZE_RATIO`](../deployment/environment-variables.md#db-resource-cache-size-ratio) environment variable and the size of the resource handle cache with the [`DB_RESOURCE_HANDLE_CACHE_SIZE_RATIO`](../deployment/environment-variables.md#db-resource-handle-cache-size-ratio) environment variable. Both specify the ratio of JVM heap size that is allocated to the cache. Together, both caches take at most 0.8 of the JVM heap.

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

Cache metrics are available under keys starting with `blaze_cache`. Among these is the `resource-cache`. These metrics can be difficult to interpret without a Prometheus/Grafana infrastructure because they are counters that accumulate since Blaze was started. Therefore, after a long runtime, it is necessary to calculate relative differences. However, shortly after Blaze starts, the absolute numbers are very useful on their own.

```sh
curl -s http://localhost:8081/metrics | grep blaze_cache | grep resource-cache
blaze_cache_hits_total{name="resource-cache",} 869000.0
blaze_cache_misses_total{name="resource-cache",} 13214.0
blaze_cache_load_successes_total{name="resource-cache",} 13214.0
blaze_cache_load_failures_total{name="resource-cache",} 0.0
blaze_cache_load_seconds_total{name="resource-cache",} 234.418864426
blaze_cache_evictions_total{name="resource-cache",} 0.0
blaze_cache_estimated_size{name="resource-cache",} 13214.0
```

The number of evictions is a key metric here. As long as the number of evictions is zero, the resource cache has not overflowed. FHIR Search inserts only the resources of the first page into the resource cache. The following pages use the resources already in the cache, but don't insert the ones they have to load, so that downloading a large search result doesn't evict the resources other queries need. The pages of the history interactions behave the same. CQL queries, however, insert every resource they load. So the goal should be for most CQL queries to fit within the resource cache. If the number of resources in a single CQL query exceeds the cache size, the cache will be evicted and refilled during that query. This is especially problematic for repeated queries, as resources needed at the beginning of the query will no longer be in the cache after it is refilled. Therefore, a cache size smaller than what is required for a single CQL query offers no performance benefit for it.

#### Resource Handle Cache

The metrics of the resource handle cache have the name `resource-handle-cache`:

```sh
curl -s http://localhost:8081/metrics | grep blaze_cache | grep resource-handle-cache
blaze_cache_hits_total{name="resource-handle-cache",} 2.5873046E7
blaze_cache_misses_total{name="resource-handle-cache",} 2421337.0
blaze_cache_load_successes_total{name="resource-handle-cache",} 0.0
blaze_cache_load_failures_total{name="resource-handle-cache",} 0.0
blaze_cache_load_seconds_total{name="resource-handle-cache",} 0.0
blaze_cache_evictions_total{name="resource-handle-cache",} 0.0
blaze_cache_estimated_size{name="resource-handle-cache",} 1176455.0
```

Every lookup of a resource handle counts as either a hit or a miss. The load metrics stay at zero, because the cache doesn't load handles itself. Instead, a handle is inserted only on its second miss, so that a single scan over more resources than the cache can hold doesn't evict the handles that other queries need. The [Resource Handle Cache](../implementation/database.md#resource-handle-cache) section of the database implementation describes the details.

The estimated size is the number of handles in the cache. A handle takes about 220 bytes with resource ids of 16 chars, or about 280 bytes with a JVM heap size of 32 GiB or more. So with the default ratio of 0.1 and a JVM heap size of 24 GiB, the cache holds about 12 million handles.

As with the resource cache, evictions show that the cache is full. Unlike the resource cache, a full resource handle cache still helps, because it keeps the handles that are looked up repeatedly. If the cache evicts handles and the number of misses grows as fast as the number of hits, the handles that the queries need repeatedly don't fit into the cache. In that case, a higher ratio helps, as long as the JVM heap has room for it.

[1]: <https://www.hl7.org/fhir/search.html#elements>
