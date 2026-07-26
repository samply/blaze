# Load Testing

## Systems

The following system was used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM | SSD                        |
|--------|----------|-------------|------:|--------:|----------------------------|
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 4 TB                       |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 2 TB Intel P5600 over vSAN |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 2 TB Huawei OceanDisk 300P |

All systems were configured according to the [Production Configuration](../production-configuration.md) guide.

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Size on SSD |
|---------|----------|---------:|---------:|---------:|------------:|
| 1M      | 10 years |      1 M |   1044 M |    593 M |    1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

## Methods

The load testing tool [k6][1] is used to create load from another host in the same network as the test system.

Each test is a k6 script in the `load-testing` directory and is run via the `Makefile`. The FHIR base URL of the system under test — for example a Blaze running in a Docker container — is passed via the `BASE` environment variable:

```sh
BASE=http://localhost:8080/fhir k6 run transaction.js
```

The optional `DURATION` environment variable (default 60 s) sets how long each concurrency level runs.

## Single Patient Reads

### Results

| Dataset | System | VUs | Req/s |  med |  q95 |  q99 |
|---------|--------|----:|------:|-----:|-----:|-----:|
| 1M      | A5N46  |   1 |  1405 | 0.50 | 0.74 | 1.47 |
| 1M      | A5N46  |   2 |  3907 | 0.45 | 0.57 | 0.67 |
| 1M      | A5N46  |   4 |  7248 | 0.53 | 0.59 | 0.69 |
| 1M      | A5N46  |   8 | 13381 | 0.55 | 0.67 | 0.88 |
| 1M      | A5N46  |  16 | 23678 | 0.60 | 0.82 | 1.21 |
| 1M      | A5N46  |  32 | 38314 | 0.73 | 1.13 | 1.90 |
| 1M      | A5N46  |  48 | 45679 | 0.89 | 1.58 | 3.22 |
| 1M      | A5N46  |  64 | 48868 | 1.07 | 2.20 | 4.12 |

## Patient Everything

### Results

| Dataset | System | VUs | Req/s |   med |   q95 |   q99 |
|---------|--------|----:|------:|------:|------:|------:|
| 1M      | A5N46  |   1 | 40.50 |  15.7 |  28.3 |  54.7 |
| 1M      | A5N46  |   2 | 73.07 |  16.9 |  31.7 |  41.7 |
| 1M      | A5N46  |   4 | 162.9 |  21.9 |  42.3 |  59.0 |
| 1M      | A5N46  |   8 | 234.9 |  30.5 |  60.1 |  90.2 |
| 1M      | A5N46  |  16 | 261.2 |  57.6 |  98.7 | 125.1 |
| 1M      | A5N46  |  32 | 258.7 | 119.4 | 174.3 | 202.1 |

## Transaction

This write test measures the throughput and latency of small [FHIR transactions][2].

The [`transaction.js`](load-testing/transaction.js) script repeatedly `POST`s a small transaction bundle to the FHIR base URL. Each bundle creates one Patient and one Observation, where the Observation references the Patient via a bundle-internal URN, so reference resolution is exercised as well. The Patient's birthDate and the Observation's systolic blood pressure are randomized per transaction, so the date and quantity search-param indices see a realistic spread of values instead of a single repeated entry. New resources are created on every request, so the database grows over the course of the run.

Both runs start from an empty database and grow it with every request, so there is no fixed dataset. The test was run on two systems to show how strongly transaction throughput depends on disk performance (see [Disk Performance](disk-perf.md)): LEA79 stores its data on a local NVMe disk with an fsync latency of a few microseconds, while LEA47 accesses its disk over vSAN with an fsync latency of about 2 ms.

### Results

| System | VUs | Req/s |    med |    q95 |    q99 |
|--------|----:|------:|-------:|-------:|-------:|
| LEA47  |   1 | 129.6 |   6.57 |   8.83 |  12.37 |
| LEA47  |   2 | 266.3 |   6.35 |   8.72 |  13.37 |
| LEA47  |   4 | 399.8 |   9.28 |  13.32 |  20.61 |
| LEA47  |   8 | 420.7 |  19.36 |  27.11 |  34.98 |
| LEA47  |  16 | 404.8 |  40.71 |  57.77 |  71.40 |
| LEA47  |  32 | 443.6 |  46.83 | 184.06 | 217.65 |
| LEA47  |  64 | 452.2 | 119.24 | 344.03 | 433.56 |
| LEA79  |   1 |  1282 |   0.53 |   0.62 |   0.68 |
| LEA79  |   2 |  2535 |   0.52 |   0.68 |   0.84 |
| LEA79  |   4 |  4639 |   0.58 |   0.78 |   0.92 |
| LEA79  |   8 |  5408 |   1.21 |   1.42 |   1.54 |
| LEA79  |  16 |  5291 |   2.79 |   3.16 |   3.39 |
| LEA79  |  32 |  5310 |   5.95 |   6.63 |   7.15 |
| LEA79  |  64 |  5617 |  11.62 |  12.96 |  15.64 |

At high concurrency LEA47 plateaus around 450 transactions/s — close to its measured fsync rate of 479/s — because every transaction must durably persist its write to disk before responding, whereas LEA79 sustains over 5000 transactions/s.

LEA47:

<LineChart src="load-testing/data/transaction-LEA47.csv"
  title="Transaction (LEA47)"
  x-log :x-min="1" :x-max="64" :x-ticks="[1, 2, 4, 8, 16, 32, 64]" />

LEA79:

<LineChart src="load-testing/data/transaction-LEA79.csv"
  title="Transaction (LEA79)"
  x-log :x-min="1" :x-max="64" :x-ticks="[1, 2, 4, 8, 16, 32, 64]" />

[1]: <https://k6.io>
[2]: <https://www.hl7.org/fhir/http.html#transaction>
