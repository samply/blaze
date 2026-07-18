CodeSystem: DiskPerfJobParameter
Id: DiskPerfJobParameter
Title: "Disk Performance Job Parameter"
* ^status = #active
* #database "Database"
* #file-size "File Size"
* #phase-duration "Phase Duration"
* #max-concurrency "Max Concurrency"

CodeSystem: DiskPerfJobOutput
Id: DiskPerfJobOutput
Title: "Disk Performance Job Output"
* ^status = #active
* #current-phase "Current Phase"
* #phase-progress "Phase Progress"
* #seq-write-throughput "Sequential Write Throughput"
* #read-iops "Random Read IOPS"
* #read-throughput "Random Read Throughput"
* #read-latency-p50 "Random Read Latency P50"
* #read-latency-p95 "Random Read Latency P95"
* #read-latency-p99 "Random Read Latency P99"
* #read-latency-max "Random Read Latency Max"
* #fsync-rate "Fsync Rate"
* #fsync-latency-p50 "Fsync Latency P50"
* #fsync-latency-p95 "Fsync Latency P95"
* #fsync-latency-p99 "Fsync Latency P99"
* #direct-io "Direct I/O Used"
* #score "Score"
* #rating "Rating"
* #processing-duration "Processing Duration"

Extension: DiskPerfConcurrency
Id: disk-perf-concurrency
Title: "Disk Performance Concurrency"
Description: "The number of concurrent readers of one run of the random read sweep of the disk performance benchmark."
* ^status = #active
* ^context[0].type = #element
* ^context[0].expression = "Task.output"
* value[x] only positiveInt

CodeSystem: DiskPerfPhase
Id: DiskPerfPhase
Title: "Disk Performance Phase"
* ^status = #active
* #seq-write "Sequential Write"
* #rand-read "Random Read"
* #fsync "Fsync"

ValueSet: DiskPerfPhase
Id: DiskPerfPhase
Title: "Disk Performance Phase Value Set"
* ^status = #active
* include codes from system DiskPerfPhase

CodeSystem: DiskPerfRating
Id: DiskPerfRating
Title: "Disk Performance Rating"
* ^status = #active
* #excellent "Excellent"
* #good "Good"
* #acceptable "Acceptable"
* #insufficient "Insufficient"

ValueSet: DiskPerfRating
Id: DiskPerfRating
Title: "Disk Performance Rating Value Set"
* ^status = #active
* include codes from system DiskPerfRating

Profile: DiskPerfJob
Parent: Job
* code = JobType#disk-perf "Measure Disk Performance"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains database 0..1
* input[database] ^short = "Database"
* input[database] ^definition = "The name of the database on which directory volume the benchmark runs. Defaults to index."
* input[database].type = DiskPerfJobParameter#database
* input[database].value[x] only code
* input[database].valueCode from Database
* input contains fileSize 0..1
* input[fileSize] obeys file-size-range
* input[fileSize] ^short = "File Size"
* input[fileSize] ^definition = "The size of the test file to write and read. Defaults to 4 GiB."
* input[fileSize].type = DiskPerfJobParameter#file-size
* input[fileSize].value[x] only Quantity
* input[fileSize].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #GiBy "gibibyte"
* input contains phaseDuration 0..1
* input[phaseDuration] obeys phase-duration-range
* input[phaseDuration] ^short = "Phase Duration"
* input[phaseDuration] ^definition = "The duration of the random read and the fsync phase. Defaults to 30 seconds."
* input[phaseDuration].type = DiskPerfJobParameter#phase-duration
* input[phaseDuration].value[x] only Quantity
* input[phaseDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"
* input contains maxConcurrency 0..1
* input[maxConcurrency] obeys max-concurrency-limit
* input[maxConcurrency] ^short = "Max Concurrency"
* input[maxConcurrency] ^definition = "The maximum number of concurrent reader threads of the random read sweep, between 1 and 128. The sweep measures one run per concurrency level, doubling the number of readers from 1 up to the maximum. Defaults to 32."
* input[maxConcurrency].type = DiskPerfJobParameter#max-concurrency
* input[maxConcurrency].value[x] only positiveInt
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains currentPhase 0..1
* output[currentPhase] ^short = "Current Phase"
* output[currentPhase] ^definition = "The phase the benchmark is currently in."
* output[currentPhase].type = DiskPerfJobOutput#current-phase
* output[currentPhase].value[x] only code
* output[currentPhase].valueCode from DiskPerfPhase
* output contains phaseProgress 0..1
* output[phaseProgress] ^short = "Phase Progress"
* output[phaseProgress] ^definition = "The progress of the current phase in percent."
* output[phaseProgress].type = DiskPerfJobOutput#phase-progress
* output[phaseProgress].value[x] only unsignedInt
* output contains seqWriteThroughput 0..1
* output[seqWriteThroughput] ^short = "Sequential Write Throughput"
* output[seqWriteThroughput] ^definition = "The throughput of the sequential write phase."
* output[seqWriteThroughput].type = DiskPerfJobOutput#seq-write-throughput
* output[seqWriteThroughput].value[x] only Quantity
* output[seqWriteThroughput].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #By/s "bytes per second"
* output contains readIops 0..*
* output[readIops] ^short = "Random Read IOPS"
* output[readIops] ^definition = "The number of I/O operations per second of one run of the random read sweep."
* output[readIops].extension contains DiskPerfConcurrency named concurrency 1..1
* output[readIops].type = DiskPerfJobOutput#read-iops
* output[readIops].value[x] only Quantity
* output[readIops].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #/s "per second"
* output contains readThroughput 0..*
* output[readThroughput] ^short = "Random Read Throughput"
* output[readThroughput] ^definition = "The throughput of one run of the random read sweep."
* output[readThroughput].extension contains DiskPerfConcurrency named concurrency 1..1
* output[readThroughput].type = DiskPerfJobOutput#read-throughput
* output[readThroughput].value[x] only Quantity
* output[readThroughput].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #By/s "bytes per second"
* output contains readLatencyP50 0..*
* output[readLatencyP50] ^short = "Random Read Latency P50"
* output[readLatencyP50] ^definition = "The median latency of a single random read in one run of the random read sweep."
* output[readLatencyP50].extension contains DiskPerfConcurrency named concurrency 1..1
* output[readLatencyP50].type = DiskPerfJobOutput#read-latency-p50
* output[readLatencyP50].value[x] only Quantity
* output[readLatencyP50].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains readLatencyP95 0..*
* output[readLatencyP95] ^short = "Random Read Latency P95"
* output[readLatencyP95] ^definition = "The 95th percentile latency of a single random read in one run of the random read sweep."
* output[readLatencyP95].extension contains DiskPerfConcurrency named concurrency 1..1
* output[readLatencyP95].type = DiskPerfJobOutput#read-latency-p95
* output[readLatencyP95].value[x] only Quantity
* output[readLatencyP95].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains readLatencyP99 0..*
* output[readLatencyP99] ^short = "Random Read Latency P99"
* output[readLatencyP99] ^definition = "The 99th percentile latency of a single random read in one run of the random read sweep."
* output[readLatencyP99].extension contains DiskPerfConcurrency named concurrency 1..1
* output[readLatencyP99].type = DiskPerfJobOutput#read-latency-p99
* output[readLatencyP99].value[x] only Quantity
* output[readLatencyP99].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains readLatencyMax 0..*
* output[readLatencyMax] ^short = "Random Read Latency Max"
* output[readLatencyMax] ^definition = "The maximum latency of a single random read in one run of the random read sweep."
* output[readLatencyMax].extension contains DiskPerfConcurrency named concurrency 1..1
* output[readLatencyMax].type = DiskPerfJobOutput#read-latency-max
* output[readLatencyMax].value[x] only Quantity
* output[readLatencyMax].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains fsyncRate 0..1
* output[fsyncRate] ^short = "Fsync Rate"
* output[fsyncRate] ^definition = "The number of write + fsync operations per second in the fsync phase."
* output[fsyncRate].type = DiskPerfJobOutput#fsync-rate
* output[fsyncRate].value[x] only Quantity
* output[fsyncRate].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #/s "per second"
* output contains fsyncLatencyP50 0..1
* output[fsyncLatencyP50] ^short = "Fsync Latency P50"
* output[fsyncLatencyP50] ^definition = "The median latency of a single write + fsync operation."
* output[fsyncLatencyP50].type = DiskPerfJobOutput#fsync-latency-p50
* output[fsyncLatencyP50].value[x] only Quantity
* output[fsyncLatencyP50].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains fsyncLatencyP95 0..1
* output[fsyncLatencyP95] ^short = "Fsync Latency P95"
* output[fsyncLatencyP95] ^definition = "The 95th percentile latency of a single write + fsync operation."
* output[fsyncLatencyP95].type = DiskPerfJobOutput#fsync-latency-p95
* output[fsyncLatencyP95].value[x] only Quantity
* output[fsyncLatencyP95].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains fsyncLatencyP99 0..1
* output[fsyncLatencyP99] ^short = "Fsync Latency P99"
* output[fsyncLatencyP99] ^definition = "The 99th percentile latency of a single write + fsync operation."
* output[fsyncLatencyP99].type = DiskPerfJobOutput#fsync-latency-p99
* output[fsyncLatencyP99].value[x] only Quantity
* output[fsyncLatencyP99].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #us "microseconds"
* output contains directIo 0..1
* output[directIo] ^short = "Direct I/O Used"
* output[directIo] ^definition = "Whether the random read phase was able to bypass the page cache using direct I/O. If false, the read numbers include page cache effects."
* output[directIo].type = DiskPerfJobOutput#direct-io
* output[directIo].value[x] only boolean
* output contains score 0..1
* output[score] ^short = "Score"
* output[score] ^definition = "The overall disk performance score between 0 and 100, where 100 represents the performance of a good local NVMe SSD."
* output[score].type = DiskPerfJobOutput#score
* output[score].value[x] only decimal
* output contains rating 0..1
* output[rating] ^short = "Rating"
* output[rating] ^definition = "The overall disk performance rating derived from the score."
* output[rating].type = DiskPerfJobOutput#rating
* output[rating].value[x] only code
* output[rating].valueCode from DiskPerfRating
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the benchmark took."
* output[processingDuration].type = DiskPerfJobOutput#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"

Invariant: file-size-range
Description: "The file size is between 1 MiB (0.0009765625 GiB) and 64 GiB."
Severity: #error
Expression: "value.ofType(Quantity).value >= 0.0009765625 and value.ofType(Quantity).value <= 64"

Invariant: phase-duration-range
Description: "The phase duration is positive and at most 300 seconds."
Severity: #error
Expression: "value.ofType(Quantity).value > 0 and value.ofType(Quantity).value <= 300"

Invariant: max-concurrency-limit
Description: "The maximum concurrency is at most 128."
Severity: #error
Expression: "value.ofType(positiveInt) <= 128"

Instance: DiskPerfJobExample
InstanceOf: DiskPerfJob
* status = #ready
* intent = #order
* code = JobType#disk-perf "Measure Disk Performance"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[database].type = DiskPerfJobParameter#database
* input[database].valueCode = Database#index
* input[fileSize].type = DiskPerfJobParameter#file-size
* input[fileSize].valueQuantity.value = 4
* input[phaseDuration].type = DiskPerfJobParameter#phase-duration
* input[phaseDuration].valueQuantity.value = 30
* input[maxConcurrency].type = DiskPerfJobParameter#max-concurrency
* input[maxConcurrency].valuePositiveInt = 32
