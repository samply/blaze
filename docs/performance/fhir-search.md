# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code in **4 million resources per seconds** and export the matching resources in **80,000 resources per seconds**, independent of the total number of resources hold.

## Systems

The following systems with rising resources were used for performance evaluation:

| System | Provider | CPU         | Cores |     RAM |    SSD |
|--------|----------|-------------|------:|--------:|-------:|
| LEA25  | on-prem  | EPYC 7543P  |     4 |  32 GiB |   2 TB | 
| LEA36  | on-prem  | EPYC 7543P  |     8 |  64 GiB |   2 TB | 
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB |   2 TB | 
| LEA58  | on-prem  | EPYC 7543P  |    32 | 256 GiB |   2 TB | 
| CCX42  | Hetzner  | EPYC 7763   |    16 |  64 GiB | 360 GB |  
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB |   2 TB |

All systems were configured according the [Tuning Guide](../tuning-guide.md).

All systems have in common that the heap mem and the block cache both use 1/4 of the total available memory each. So the Blaze process itself will only use about half the system memory available. The rest of the system memory will be used as file system cache.

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Size on SSD |
|---------|----------|---------:|---------:|---------:|------------:|
| 100k    | 10 years |    100 k |    104 M |     59 M |     202 GiB |
| 1M      | 10 years |      1 M |   1044 M |    593 M |    1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

The creation of the datasets is described in the [Synthea section](./synthea/README.md). The disc size is measured after full manual compaction of the database. The actual disc size will be up to 50% higher, depending on the state of compaction which happens regularly in the background.

## Simple Code Search

In this section, FHIR Search for selecting Observation resources with a certain code is used.

### Counting

Counting is done using the following `curl` command:

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
| LEA47  | 1M      | 8310-5  |  1.1 M |     0.59 |  0.006 |  1.86 M |
| LEA47  | 1M      | 55758-7 | 10.1 M |     5.45 |  0.182 |  1.85 M |
| LEA47  | 1M      | 72514-3 | 27.3 M |    14.03 |  0.135 |  1.95 M |
| LEA58  | 1M      | 8310-5  |  1.1 M |     0.60 |  0.011 |  1.83 M |
| LEA58  | 1M      | 55758-7 | 10.1 M |     5.17 |  0.077 |  1.95 M |
| LEA58  | 1M      | 72514-3 | 27.3 M |    13.77 |  0.244 |  1.98 M |
| A5N46  | 1M      | 8310-5  |  1.1 M |     0.23 |  0.009 |   4.9 M |
| A5N46  | 1M      | 55758-7 | 10.1 M |     2.45 |  0.035 |   4.1 M |
| A5N46  | 1M      | 72514-3 | 27.3 M |     4.24 |  0.024 |   6.4 M |

¹ resources per second

According to the measurements the time needed by Blaze to count resources is independent of the number of hits and equals roughly **2 million resources per seconds** for the LEA systems and **4 million resources per seconds** for the A5N46 system..

### Download of Resources

![](fhir-search/simple-code-search-download-1M.png)

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |   Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|----------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.90 |  0.016 |   60.53 k |  
| LEA36  | 100k    | 55758-7 |  1.0 M |    15.45 |  0.121 |   64.72 k |
| LEA36  | 100k    | 72514-3 |  2.7 M |    41.09 |  0.530 |   65.71 k |
| LEA47  | 100k    | 8310-5  |  115 k |     2.00 |  0.021 |   57.50 k |  
| LEA47  | 100k    | 55758-7 |  1.0 M |    15.99 |  0.147 |   62.54 k |
| LEA47  | 100k    | 72514-3 |  2.7 M |    43.48 |  0.128 |   62.10 k |
| LEA58  | 100k    | 8310-5  |  115 k |     1.96 |  0.039 |   58.67 k |  
| LEA58  | 100k    | 55758-7 |  1.0 M |    16.61 |  0.161 |   60.20 k |
| LEA58  | 100k    | 72514-3 |  2.7 M |    43.84 |  0.124 |   61.59 k |         
| LEA47  | 1M      | 8310-5  |  1.1 M |    19.20 |  0.102 |   57.29 k |         
| LEA47  | 1M      | 55758-7 | 10.1 M |   206.52 |  1.478 | 48.91 k ² |         
| LEA47  | 1M      | 72514-3 | 27.3 M |   673.10 |  3.072 | 40.56 k ² |
| LEA58  | 1M      | 8310-5  |  1.1 M |    18.95 |  0.075 |   58.05 k |         
| LEA58  | 1M      | 55758-7 | 10.1 M |   160.31 |  0.976 |   63.00 k |         
| LEA58  | 1M      | 72514-3 | 27.3 M |   498.38 |  9.773 | 54.78 k ² |
| A5N46  | 1M      | 8310-5  |  1.1 M |     7.70 |  0.017 |   150.5 k |         
| A5N46  | 1M      | 55758-7 | 10.1 M |   156.31 |  1.455 |  64.9 k ² |        
| A5N46  | 1M      | 72514-3 | 27.3 M |   337.97 |  5.626 |  80.9 k ² |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

According to the measurements the time needed by Blaze to deliver resources is independent of the number of hits and equals roughly **60,000 resources per seconds** for the LEA systems and **80,000 resources per seconds** for the A5N46 system.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

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
| LEA47  | 1M      | 8310-5  |  1.1 M |    13.14 |  0.540 |   83.71 k |          
| LEA47  | 1M      | 55758-7 | 10.1 M |   145.59 |  0.618 | 69.37 k ² |          
| LEA47  | 1M      | 72514-3 | 27.3 M |   437.43 |  3.461 | 62.41 k ² |
| LEA58  | 1M      | 8310-5  |  1.1 M |    12.28 |  0.351 |   89.58 k |          
| LEA58  | 1M      | 55758-7 | 10.1 M |   103.67 |  1.057 |   97.42 k |          
| LEA58  | 1M      | 72514-3 | 27.3 M |   309.76 |  1.580 | 88.13 k ² |
| A5N46  | 1M      | 8310-5  |  1.1 M |     5.05 |  0.022 |   229.5 k |          
| A5N46  | 1M      | 55758-7 | 10.1 M |   149.02 |  0.160 |  68.0 k ² |          
| A5N46  | 1M      | 72514-3 | 27.3 M |   262.63 |  0.605 | 104.1 k ² |          

¹ resources per second, ² resource cache size is smaller than the number of resources returned

According to the measurements, the time needed by Blaze to deliver subsetted Observations containing only the subject reference is independent of the number of hits and equals roughly **90,000 resources per seconds** for the LEA systems and **140,000 resources per seconds** for the A5N46 system.

## Multiple Codes Search

In this section, FHIR Search for selecting Observation resources with a multiple codes.

The codes used are the following top 20 LOINC codes:

```
72514-3,49765-1,20565-8,2069-3,38483-4,2339-0,6298-4,2947-0,6299-2,85354-9,29463-7,8867-4,9279-1,8302-2,72166-2,39156-5,93025-5,74006-8,55758-7,33914-3
```

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_summary=count"
```

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|

¹ resources per second

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_count=1000" > /dev/null
```

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|

¹ resources per second

## Multiple Search Param Search

In this section, FHIR search queries with multiple search params and multiple codes are used.

Two sets of codes were used:

| Category    | Name  | Codes                                     |
|-------------|-------|-------------------------------------------|
| laboratory  | top-5 | 49765-1, 20565-8, 2069-3, 38483-4, 2339-0 |
| vital-signs | low-5 | 2713-6, 8478-0, 8310-5, 77606-2, 9843-4   |

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?status=final&category=$CATEGORY&code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_summary=count"
```

| System | Dataset | Category    | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |   170.46 |  0.912 |  20.7 k |

¹ resources per second

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "status=final&category=$CATEGORY&code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_count=1000" > /dev/null
```

| System | Dataset | Category    | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |  1348.99 |  0.728 |   2.6 k |

¹ resources per second

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "status=final&category=$CATEGORY&code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&_elements=subject&_count=1000" > /dev/null
```

| System | Dataset | Category    | Codes | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------------|------:|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | vital-signs | low-5 |  3.5 M |  1414.20 | 51.859 |   2.5 k |

¹ resources per second

## Code and Value Search

In this section, FHIR Search for selecting Observation resources with a certain code and value is used.

### Counting

Counting is done using the following `curl` command:

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

¹ time in seconds per 1 million resources, ² block cache hit ratio is near zero

The measurements show that the time Blaze needs to count resources with two search params (code and value-quantity) is constant. In fact it depends only on the number of resources which qualify for the first search parameter which can be seen on the fixed time of 4 seconds.

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:
 
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

¹ time in seconds per 1 million resources, ² block cache hit ratio is near zero

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

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

In this section, FHIR Search for selecting Observation resources with a certain code at a certain date.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&date=$DATE&_summary=count"
```

| System | Dataset | Code   | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------|-----:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5 | 2013 |   50 k |     0.58 |  0.003 |  86.6 k |
| LEA47  | 1M      | 8310-5 | 2019 |   85 k |     0.59 |  0.003 | 145.3 k |
| LEA47  | 1M      | 8310-5 | 2020 |  273 k |     0.59 |  0.003 | 465.8 k |
| A5N46  | 1M      | 8310-5 | 2013 |   50 k |     1.49 |  0.006 |  33.9 k | 
| A5N46  | 1M      | 8310-5 | 2019 |   85 k |     1.49 |  0.005 |  57.0 k | 
| A5N46  | 1M      | 8310-5 | 2020 |  273 k |     1.48 |  0.008 | 184.1 k |

¹ resources per second

### Download of Resources

All measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=$DATE&_count=1000" > /dev/null
```

| System | Dataset | Code   | Date | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------|-----:|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5 | 2013 |   50 k |     8.45 |  0.014 |   6.0 k |
| LEA47  | 1M      | 8310-5 | 2019 |   85 k |     9.10 |  0.031 |   9.4 k |
| LEA47  | 1M      | 8310-5 | 2020 |  273 k |    12.20 |  0.111 |  22.4 k |
| A5N46  | 1M      | 8310-5 | 2013 |   50 k |    18.18 |  0.031 |   2.8 k |
| A5N46  | 1M      | 8310-5 | 2019 |   85 k |    18.47 |  0.017 |   4.6 k |
| A5N46  | 1M      | 8310-5 | 2020 |  273 k |    19.53 |  0.012 |  14.0 k |

¹ resources per second

## Code and Patient Search

In this section, FHIR Search for selecting Observation resources with a certain code and 1000 Patients is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.001 |    92 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.05 |  0.001 |   551 k |
| A5N46  | 1M      | 72514-3 |  113 k |     0.14 |  0.006 |   793 k |

¹ resources per second

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | 8310-5  |    944 |     0.03 |  0.000 |    31 k |
| A5N46  | 1M      | 55758-7 |   28 k |     1.10 |  0.017 |    26 k |
| A5N46  | 1M      | 72514-3 |  113 k |     9.74 |  0.059 |    12 k |

¹ resources per second

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      | 8310-5  |    944 |     0.02 |  0.005 |    57 k |
| A5N46  | 1M      | 55758-7 |   28 k |     0.99 |  0.009 |    29 k |
| A5N46  | 1M      | 72514-3 |  113 k |     8.93 |  0.120 |    13 k |

¹ resources per second

## Multiple Codes and Patient Search

In this section, FHIR Search for selecting Observation resources with a multiple codes and 1000 Patients is used.

The codes used are the following top 20 LOINC codes:

```
72514-3,49765-1,20565-8,2069-3,38483-4,2339-0,6298-4,2947-0,6299-2,85354-9,29463-7,8867-4,9279-1,8302-2,72166-2,39156-5,93025-5,74006-8,55758-7,33914-3
```

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      |  1.1 M |     0.96 |  0.009 |   1.1 M |

¹ resources per second

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE_1,http://loinc.org|$CODE_2&patient=$PATIENT_IDS&_count=1000" > /dev/null
```

| System | Dataset | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|-------:|---------:|-------:|--------:|
| A5N46  | 1M      |  1.1 M |   551.51 |  0.119 |   2.0 k |

¹ resources per second

## Code, Date and Patient Search

In this section, FHIR Search for selecting Observation resources with a certain code, a certain date and 1000 Patients is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.03 |  0.002 |   8.1 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.20 |  0.003 |  14.3 k |
| LEA47  | 1M      | 72514-3 |   12 k |     0.56 |  0.005 |  21.3 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.001 |  18.9 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.10 |  0.001 |  27.7 k |
| A5N46  | 1M      | 72514-3 |   12 k |     0.29 |  0.002 |  41.5 k |

¹ resources per second

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_count=1000" > /dev/null
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.04 |  0.000 |   6.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.46 |  0.005 |   6.1 k |
| LEA47  | 1M      | 72514-3 |   12 k |     3.97 |  0.009 |   3.0 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.02 |  0.005 |  14.8 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.26 |  0.005 |  11.0 k |
| A5N46  | 1M      | 72514-3 |   12 k |     2.03 |  0.008 |   5.9 k |

¹ resources per second

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | 8310-5  |    246 |     0.03 |  0.000 |   8.2 k |
| LEA47  | 1M      | 55758-7 |    3 k |     0.43 |  0.005 |   6.5 k |
| LEA47  | 1M      | 72514-3 |   12 k |     3.75 |  0.037 |   3.2 k |
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.000 |  24.6 k |
| A5N46  | 1M      | 55758-7 |    3 k |     0.25 |  0.005 |  11.4 k |
| A5N46  | 1M      | 72514-3 |   12 k |     1.96 |  0.000 |   6.1 k |

¹ resources per second

## Simple Date Search

In this section, FHIR Search for selecting Observation resources with a certain effective year is used.

### Counting

Counting is done using the following `curl` command:

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
| A5N46  | 1M      | 2013 | 31.1 M |     5.65 |  0.056 |   5.5 M |
| A5N46  | 1M      | 2019 | 60.0 M |    10.16 |  0.234 |   5.9 M |

¹ resources per second

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

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
| A5N46  | 1M      | 2013 | 31.1 M |   337.31 |  0.646 | 92.1 k ² |
| A5N46  | 1M      | 2019 | 60.0 M |   637.94 |  3.090 | 94.1 k ² |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

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
| A5N46  | 1M      | 2013 | 31.1 M |   249.61 |  1.193 | 124.5 k ² |
| A5N46  | 1M      | 2019 | 60.0 M |   461.02 |  5.281 | 130.3 k ² |

¹ resources per second, ² resource cache size is smaller than the number of resources returned

## Patient Date Search

In this section, FHIR Search for selecting Patient resources with a certain birth date is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Patient?birthdate=$DATE&_summary=count"
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     0.31 |  0.004 | 744.7 k |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     0.34 |  0.005 | 672.6 k |
| LEA47  | 1M      | lt1998-04-10 |  773 k |     0.34 |  0.006 |   2.3 M |
| LEA47  | 1M      | le1998-04-10 |  773 k |     0.36 |  0.012 |   2.2 M |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     0.15 |  0.002 |   1.5 M |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     0.16 |  0.007 |   1.4 M |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     0.20 |  0.007 |   3.8 M |
| A5N46  | 1M      | le1998-04-10 |  773 k |     0.21 |  0.010 |   3.7 M |

¹ resources per second

### Download of Resources

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q "birthdate=$DATE&_count=1000" > /dev/null
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |    10.89 |  0.087 |  20.9 k |
| LEA47  | 1M      | ge1998-04-10 |  227 k |    10.88 |  0.092 |  20.9 k |
| LEA47  | 1M      | lt1998-04-10 |  773 k |    39.62 |  0.401 |  19.5 k |
| LEA47  | 1M      | le1998-04-10 |  773 k |    39.09 |  0.033 |  19.8 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     5.01 |  0.012 |  45.4 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     4.96 |  0.019 |  45.8 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |    18.59 |  0.271 |  41.6 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |    18.63 |  0.299 |  41.5 k |

¹ resources per second

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=id` was used.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q "birthdate=$DATE&_elements=id&_count=1000" > /dev/null
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | Res/s ¹ |
|--------|---------|--------------|-------:|---------:|-------:|--------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     3.24 |  0.036 |  70.2 k |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     3.24 |  0.040 |  70.1 k |
| LEA47  | 1M      | lt1998-04-10 |  773 k |    10.39 |  0.180 |  74.4 k |
| LEA47  | 1M      | le1998-04-10 |  773 k |    10.36 |  0.289 |  74.6 k |
| A5N46  | 1M      | gt1998-04-10 |  227 k |     1.24 |  0.034 | 182.9 k |
| A5N46  | 1M      | ge1998-04-10 |  227 k |     1.22 |  0.036 | 186.4 k |
| A5N46  | 1M      | lt1998-04-10 |  773 k |     3.83 |  0.079 | 201.6 k |
| A5N46  | 1M      | le1998-04-10 |  773 k |     3.86 |  0.127 | 200.2 k |

¹ resources per second

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
