# Raw HTTP Performance

## FHIR Search First Page

The following tests requests the first page of Patients using FHIR search.

```sh
http/search-type-patient.sh
```

The results show that 2000 requests/second are possible with a 99 % quantile of less than 50 ms.

```text
Requests      [total, rate, throughput]         600000, 1999.98, 1999.95
Duration      [total, attack, wait]             5m0s, 5m0s, 5.66ms
Latencies     [min, mean, 50, 90, 95, 99, max]  1.852ms, 14.032ms, 12.521ms, 24.126ms, 28.316ms, 37.309ms, 76.375ms
Bytes In      [total, mean]                     96043200000, 160072.00
Bytes Out     [total, mean]                     0, 0.00
Success       [ratio]                           100.00%
Status Codes  [code:count]                      200:600000 
```

## Used Tooling

The HTTP requests were generated using [Vegeta](https://github.com/tsenart/vegeta).
