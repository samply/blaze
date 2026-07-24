Instance: disk-perf
InstanceOf: OperationDefinition
Usage: #definition
* url = "https://blaze-server.org/fhir/OperationDefinition/disk-perf"
* name = "DiskPerf"
* title = "Measure Disk Performance"
* status = #active
* kind = #operation
* description = "Measures the performance of the disk volume of one of the database directories with an I/O profile similar to the one Blaze's RocksDB databases produce. The response is always async according to the Asynchronous Interaction Request Pattern. The results are also available as outputs of the underlying DiskPerfJob Task resource."
* affectsState = true
* code = #disk-perf
* system = true
* type = false
* instance = false
* parameter[+]
  * name = #database
  * use = #in
  * min = 0
  * max = "1"
  * documentation = "The name of the database on which directory volume the benchmark runs. Defaults to index."
  * type = #code
  * binding
    * strength = #required
    * valueSet = "https://blaze-server.org/fhir/ValueSet/Database"
* parameter[+]
  * name = #file-size
  * use = #in
  * min = 0
  * max = "1"
  * documentation = "The size of the test file to write and read in GiB, at least 1 MiB and at most 64 GiB. Defaults to 4."
  * type = #decimal
* parameter[+]
  * name = #phase-duration
  * use = #in
  * min = 0
  * max = "1"
  * documentation = "The duration of the random read and the fsync phase in seconds, at most 300. Defaults to 30."
  * type = #decimal
* parameter[+]
  * name = #max-concurrency
  * use = #in
  * min = 0
  * max = "1"
  * documentation = "The maximum number of concurrent reader threads of the random read sweep, between 1 and 1024. The sweep measures one run per concurrency level, doubling the number of readers from 1 up to the maximum. Defaults to 32."
  * type = #positiveInt
* parameter[+]
  * name = #seq-write-throughput
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The throughput of the sequential write phase in bytes per second."
  * type = #Quantity
* parameter[+]
  * name = #rand-read
  * use = #out
  * min = 1
  * max = "*"
  * documentation = "One run of the random read sweep per concurrency level."
  * part[+]
    * name = #concurrency
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The number of concurrent reader threads of this run."
    * type = #positiveInt
  * part[+]
    * name = #iops
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The number of I/O operations per second of this run."
    * type = #Quantity
  * part[+]
    * name = #throughput
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The throughput of this run in bytes per second."
    * type = #Quantity
  * part[+]
    * name = #latency-p50
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The median latency of a single random read of this run in microseconds."
    * type = #Quantity
  * part[+]
    * name = #latency-p95
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The 95th percentile latency of a single random read of this run in microseconds."
    * type = #Quantity
  * part[+]
    * name = #latency-p99
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The 99th percentile latency of a single random read of this run in microseconds."
    * type = #Quantity
  * part[+]
    * name = #latency-max
    * use = #out
    * min = 1
    * max = "1"
    * documentation = "The maximum latency of a single random read of this run in microseconds."
    * type = #Quantity
* parameter[+]
  * name = #fsync-rate
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The number of write + fsync operations per second in the fsync phase."
  * type = #Quantity
* parameter[+]
  * name = #fsync-latency-p50
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The median latency of a single write + fsync operation in microseconds."
  * type = #Quantity
* parameter[+]
  * name = #fsync-latency-p95
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The 95th percentile latency of a single write + fsync operation in microseconds."
  * type = #Quantity
* parameter[+]
  * name = #fsync-latency-p99
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The 99th percentile latency of a single write + fsync operation in microseconds."
  * type = #Quantity
* parameter[+]
  * name = #direct-io
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "Whether the random read phase was able to bypass the page cache using direct I/O. If false, the read results include page cache effects."
  * type = #boolean
* parameter[+]
  * name = #score
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The overall disk performance score between 0 and 100, where 100 represents the performance of a good local NVMe SSD."
  * type = #decimal
* parameter[+]
  * name = #rating
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The overall disk performance rating derived from the score."
  * type = #code
  * binding
    * strength = #required
    * valueSet = "https://blaze-server.org/fhir/ValueSet/DiskPerfRating"
* parameter[+]
  * name = #processing-duration
  * use = #out
  * min = 1
  * max = "1"
  * documentation = "The duration the whole benchmark took in seconds."
  * type = #Quantity
