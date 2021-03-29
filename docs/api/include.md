### Include 

Download all Condition resources including their referenced Patient resources.

```sh
$ blazectl --server http://localhost:8080/fhir download -t Condition \
  -q '_include=Condition:subject&_count=1000' -o Condition-Patient.ndjson
  
Pages		[total]			2779
Resources 	[total]			2779075
Resources/Page	[min, mean, max]	957, 1000, 1001
Duration	[total]			14m15s
Requ. Latencies	[mean, 50, 95, 99, max]	276ms, 271ms, 341ms, 375ms, 769ms
Proc. Latencies	[mean, 50, 95, 99, max]	270ms, 266ms, 335ms, 368ms, 758ms
Bytes In	[total, mean]		3.07 GiB, 1.13 MiB
```

### Revinclude

Download all Patient resources including all Condition resources that refer to them.

```sh
blazectl --server http://localhost:8080/fhir download -t Patient \
  -q '_revinclude=Condition:subject&_count=1000' \
  -o Patient-Condition.ndjson
```

Download all Patient resources including all Condition, Observation and Procedure resources that refer to them.

```sh
blazectl --server http://localhost:8080/fhir download -t Patient \
  -q '_revinclude=Condition:subject&_revinclude=Observation:subject&_revinclude=Procedure:subject&_count=1000' \
  -o Patient-Condition-Observation-Procedure.ndjson
```
