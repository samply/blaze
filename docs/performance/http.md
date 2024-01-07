# Raw HTTP Performance

## FHIR Search First Page

The following tests requests the first page of Patients using FHIR search.

```sh
http/search-type-patient.sh
```

The results show that 2000 requests/second are possible with a 99 % quantile of less than 5 ms.

```text
Requests      [total, rate, throughput]         600000, 2000.00, 1999.99
Duration      [total, attack, wait]             5m0s, 5m0s, 1.557ms
Latencies     [min, mean, 50, 90, 95, 99, max]  986.209µs, 1.877ms, 1.626ms, 2.29ms, 2.55ms, 3.613ms, 141.876ms
Bytes In      [total, mean]                     106884000000, 178140.00
Bytes Out     [total, mean]                     0, 0.00
Success       [ratio]                           100.00%
Status Codes  [code:count]                      200:600000 
```

## Used Tooling

The HTTP requests were generated using [Vegeta](https://github.com/tsenart/vegeta).
