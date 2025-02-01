# FHIR Search Performance

## TL;DR

Under ideal conditions, Blaze can execute a FHIR Search query for a single code in **0.5 seconds per 1 million found resources** and export the matching resources in **17 seconds per 1 million found resources**, independent of the total number of resources hold.

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

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| LEA36  | 100k    | 8310-5  |  115 k |     0.06 |  0.003 |   0.55 |
| LEA36  | 100k    | 55758-7 |  1.0 M |     0.49 |  0.005 |   0.48 |
| LEA36  | 100k    | 72514-3 |  2.7 M |     1.28 |  0.022 |   0.46 |
| LEA47  | 100k    | 8310-5  |  115 k |     0.06 |  0.001 |   0.54 |
| LEA47  | 100k    | 55758-7 |  1.0 M |     0.50 |  0.010 |   0.49 |
| LEA47  | 100k    | 72514-3 |  2.7 M |     1.34 |  0.026 |   0.48 |
| LEA58  | 100k    | 8310-5  |  115 k |     0.07 |  0.002 |   0.57 |
| LEA58  | 100k    | 55758-7 |  1.0 M |     0.54 |  0.020 |   0.53 |
| LEA58  | 100k    | 72514-3 |  2.7 M |     1.43 |  0.045 |   0.52 |
| LEA47  | 1M      | 8310-5  |  1.1 M |     0.59 |  0.006 |   0.50 |
| LEA47  | 1M      | 55758-7 | 10.1 M |     5.45 |  0.182 |   0.53 |
| LEA47  | 1M      | 72514-3 | 27.3 M |    14.03 |  0.135 |   0.51 |
| LEA58  | 1M      | 8310-5  |  1.1 M |     0.60 |  0.011 |   0.51 |
| LEA58  | 1M      | 55758-7 | 10.1 M |     5.17 |  0.077 |   0.50 |
| LEA58  | 1M      | 72514-3 | 27.3 M |    13.77 |  0.244 |   0.50 |

¹ time in seconds per 1 million resources

According to the measurements the time needed by Blaze to count resources only depends on the number of hits and equals roughly in **0.5 seconds per 1 million hits**.

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.90 |  0.016 |   16.49 |  
| LEA36  | 100k    | 55758-7 |  1.0 M |    15.45 |  0.121 |   15.33 |
| LEA36  | 100k    | 72514-3 |  2.7 M |    41.09 |  0.530 |   14.95 |
| LEA47  | 100k    | 8310-5  |  115 k |     2.00 |  0.021 |   17.33 |  
| LEA47  | 100k    | 55758-7 |  1.0 M |    15.99 |  0.147 |   15.87 |
| LEA47  | 100k    | 72514-3 |  2.7 M |    43.48 |  0.128 |   15.82 |
| LEA58  | 100k    | 8310-5  |  115 k |     1.96 |  0.039 |   17.04 |  
| LEA58  | 100k    | 55758-7 |  1.0 M |    16.61 |  0.161 |   16.48 |
| LEA58  | 100k    | 72514-3 |  2.7 M |    43.84 |  0.124 |   15.95 |         
| LEA47  | 1M      | 8310-5  |  1.1 M |    19.20 |  0.102 |   16.56 |         
| LEA47  | 1M      | 55758-7 | 10.1 M |   206.52 |  1.478 | 20.36 ² |         
| LEA47  | 1M      | 72514-3 | 27.3 M |   673.10 |  3.072 | 24.61 ² |
| LEA58  | 1M      | 8310-5  |  1.1 M |    18.95 |  0.075 |   16.34 |         
| LEA58  | 1M      | 55758-7 | 10.1 M |   160.31 |  0.976 |   15.80 |         
| LEA58  | 1M      | 72514-3 | 27.3 M |   498.38 |  9.773 | 18.22 ² |         

¹ time in seconds per 1 million resources, ² resource cache size is smaller than the number of resources returned

According to the measurements the time needed by Blaze to deliver resources only depends on the number of hits and equals roughly in **17 seconds per 1 million hits**.

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 8310-5  |  115 k |     1.26 |  0.009 |   10.96 |
| LEA36  | 100k    | 55758-7 |  1.0 M |     9.70 |  0.070 |    9.63 |
| LEA36  | 100k    | 72514-3 |  2.7 M |    25.82 |  0.440 |    9.39 |
| LEA47  | 100k    | 8310-5  |  115 k |     1.34 |  0.009 |   11.60 |
| LEA47  | 100k    | 55758-7 |  1.0 M |     9.95 |  0.065 |    9.87 |
| LEA47  | 100k    | 72514-3 |  2.7 M |    26.76 |  0.284 |    9.73 |
| LEA58  | 100k    | 8310-5  |  115 k |     1.28 |  0.017 |   11.08 |
| LEA58  | 100k    | 55758-7 |  1.0 M |    10.55 |  0.209 |   10.47 |
| LEA58  | 100k    | 72514-3 |  2.7 M |    27.15 |  0.749 |    9.87 |
| LEA47  | 1M      | 8310-5  |  1.1 M |    13.14 |  0.540 |   11.33 |          
| LEA47  | 1M      | 55758-7 | 10.1 M |   145.59 |  0.618 | 14.35 ² |          
| LEA47  | 1M      | 72514-3 | 27.3 M |   437.43 |  3.461 | 15.99 ² |
| LEA58  | 1M      | 8310-5  |  1.1 M |    12.28 |  0.351 |   10.59 |          
| LEA58  | 1M      | 55758-7 | 10.1 M |   103.67 |  1.057 |   10.22 |          
| LEA58  | 1M      | 72514-3 | 27.3 M |   309.76 |  1.580 | 11.32 ² |          

¹ time in seconds per 1 million resources, ² resource cache size is smaller than the number of resources returned

According to the measurements, the time needed by Blaze to deliver subsetted Observations containing only the subject reference only depends on the number of hits and equals roughly in **11 seconds per 1 million hits**.

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
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_count=1000" > /dev/null"
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
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject&_count=1000" > /dev/null"
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

## Code and Patient Search

In this section, FHIR Search for selecting Observation resources with a certain code and 1000 Patients is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.002 |  11.07 |
| A5N46  | 1M      | 55758-7 |   28 k |     0.04 |  0.001 |   1.59 |
| A5N46  | 1M      | 72514-3 |  113 k |     0.13 |  0.004 |   1.10 |

¹ time in seconds per 1 million resources

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| A5N46  | 1M      | 8310-5  |    944 |     0.03 |  0.005 |  28.24 |
| A5N46  | 1M      | 55758-7 |   28 k |     0.99 |  0.024 |  35.27 |
| A5N46  | 1M      | 72514-3 |  113 k |     8.34 |  0.176 |  73.74 |

¹ time in seconds per 1 million resources

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| A5N46  | 1M      | 8310-5  |    944 |     0.01 |  0.000 |  10.59 |
| A5N46  | 1M      | 55758-7 |   28 k |     0.83 |  0.022 |  29.47 |
| A5N46  | 1M      | 72514-3 |  113 k |     7.88 |  0.070 |  69.61 |

¹ time in seconds per 1 million resources

## Code, Date and Patient Search

In this section, FHIR Search for selecting Observation resources with a certain code, a certain date and 1000 Patients is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_summary=count"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.001 |  55.84 |
| A5N46  | 1M      | 55758-7 |    3 k |     0.10 |  0.003 |  34.52 |
| A5N46  | 1M      | 72514-3 |   12 k |     0.29 |  0.007 |  23.84 |

¹ time in seconds per 1 million resources

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.005 |  54.19 |
| A5N46  | 1M      | 55758-7 |    3 k |     0.26 |  0.000 |  92.00 |
| A5N46  | 1M      | 72514-3 |   12 k |     1.94 |  0.008 | 162.19 |

¹ time in seconds per 1 million resources

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=subject` was used.

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&date=2020&patient=$PATIENT_IDS&_elements=subject&_count=1000" > /dev/null"
```

| System | Dataset | Code    | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|---------|-------:|---------:|-------:|-------:|
| A5N46  | 1M      | 8310-5  |    246 |     0.01 |  0.000 |  40.65 |
| A5N46  | 1M      | 55758-7 |    3 k |     0.24 |  0.005 |  83.74 |
| A5N46  | 1M      | 72514-3 |   12 k |     1.92 |  0.031 | 160.80 |

¹ time in seconds per 1 million resources

## Simple Date Search

In this section, FHIR Search for selecting Observation resources with a certain effective year is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Observation?date=$YEAR&_summary=count"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|------|-------:|---------:|-------:|-------:|
| LEA36  | 100k    | 2013 |  3.1 M |     2.12 |  0.023 |   0.67 |
| LEA36  | 100k    | 2019 |  6.0 M |     3.81 |  0.062 |   0.63 |
| LEA47  | 100k    | 2013 |  3.1 M |     2.13 |  0.068 |   0.68 |
| LEA47  | 100k    | 2019 |  6.0 M |     4.17 |  0.175 |   0.69 |
| LEA58  | 100k    | 2013 |  3.1 M |     2.22 |  0.071 |   0.71 |
| LEA58  | 100k    | 2019 |  6.0 M |     4.19 |  0.056 |   0.70 |
| LEA47  | 1M      | 2013 | 31.1 M |    21.28 |  0.439 |   0.68 |
| LEA47  | 1M      | 2019 | 60.0 M |    42.58 |  1.502 |   0.70 |

¹ time in seconds per 1 million resources

### Download of Resources

Most measurements are done after Blaze is in a steady state with all resources to download in it's resource cache in order to cancel out resource load times from disk or file system cache ².

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Observation -q "date=$YEAR&_count=1000" > /dev/null"
```

| System | Dataset | Year | # Hits | Time (s) | StdDev |  T/1M ¹ |
|--------|---------|------|-------:|---------:|-------:|--------:|
| LEA36  | 100k    | 2013 |  3.1 M |    52.42 |  0.545 |   16.77 |
| LEA36  | 100k    | 2019 |  6.0 M |   129.84 |  5.393 | 21.71 ² |
| LEA47  | 100k    | 2013 |  3.1 M |    53.41 |  0.377 |   17.08 |
| LEA47  | 100k    | 2019 |  6.0 M |   107.15 |  0.116 |   17.92 |
| LEA58  | 100k    | 2013 |  3.1 M |    53.07 |  0.090 |   16.98 |
| LEA58  | 100k    | 2019 |  6.0 M |   100.73 |  0.473 |   16.84 |
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
| LEA36  | 100k    | 2013 |  3.1 M |    32.07 |  0.278 |   10.26 |
| LEA36  | 100k    | 2019 |  6.0 M |    79.92 |  4.179 |   13.36 |
| LEA47  | 100k    | 2013 |  3.1 M |    31.99 |  0.061 |   10.23 |
| LEA47  | 100k    | 2019 |  6.0 M |    66.33 |  0.045 |   11.09 |
| LEA58  | 100k    | 2013 |  3.1 M |    32.43 |  0.340 |   10.37 |
| LEA58  | 100k    | 2019 |  6.0 M |    62.14 |  0.488 |   10.39 |
| LEA47  | 1M      | 2013 | 31.1 M |   673.36 | 10.199 | 21.67 ² |
| LEA47  | 1M      | 2019 | 60.0 M |  1516.90 |  0.482 | 25.25 ² |

¹ time in seconds per 1 million resources, ² resource cache size is smaller than the number of resources returned

## Patient Date Search

In this section, FHIR Search for selecting Patient resources with a certain birth date is used.

### Counting

Counting is done using the following `curl` command:

```sh
curl -s "http://localhost:8080/fhir/Patient?birthdate=$DATE&_summary=count"
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|--------------|-------:|---------:|-------:|-------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     0.38 |  0.005 |   1.68 |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     0.40 |  0.007 |   1.74 |
| LEA47  | 1M      | lt1998-04-10 |  773 k |     0.58 |  0.017 |   0.75 |
| LEA47  | 1M      | le1998-04-10 |  773 k |     0.60 |  0.005 |   0.78 |

### Download of Resources

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q "birthdate=$DATE&_count=1000" > /dev/null"
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|--------------|-------:|---------:|-------:|-------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     7.77 |  0.033 |  34.17 |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     7.91 |  0.056 |  34.77 |
| LEA47  | 1M      | lt1998-04-10 |  773 k |    26.85 |  0.065 |  34.74 |
| LEA47  | 1M      | le1998-04-10 |  773 k |    27.73 |  0.012 |  35.88 |

### Download of Resources with Subsetting

In case only a subset of information of a resource is needed, the special [_elements][1] search parameter can be used to retrieve only certain properties of a resource. Here `_elements=id` was used.

Download is done using the following `blazectl` command:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q "birthdate=$DATE&_elements=id&_count=1000" > /dev/null"
```

| System | Dataset | Date         | # Hits | Time (s) | StdDev | T/1M ¹ |
|--------|---------|--------------|-------:|---------:|-------:|-------:|
| LEA47  | 1M      | gt1998-04-10 |  227 k |     3.15 |  0.016 |  13.85 |
| LEA47  | 1M      | ge1998-04-10 |  227 k |     3.09 |  0.108 |  13.58 |
| LEA47  | 1M      | lt1998-04-10 |  773 k |     9.90 |  0.249 |  12.81 |
| LEA47  | 1M      | le1998-04-10 |  773 k |     9.73 |  0.073 |  12.59 |

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
