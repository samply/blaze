CodeSystem: CompactJobParameter
Id: CompactJobParameter
Title: "Compact Job Parameter"
Description: "Types of the inputs of a compact job."
* insert ShareableCodeSystem
* #database "Database"
* #column-family "Column Family"

CodeSystem: CompactJobOutput
Id: CompactJobOutput
Title: "Compact Job Output"
Description: "Types of the outputs of a compact job."
* insert ShareableCodeSystem
* #processing-duration "Processing Duration"

Profile: CompactJob
Parent: Job
Description: "A job compacting one column family of one of the databases of Blaze."
* code = JobType#compact "Compact a Database Column Family"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains database 1..1
* input[database] ^short = "Database"
* input[database] ^definition = "The name of the database to compact."
* input[database].type = CompactJobParameter#database
* input[database].value[x] only code
* input[database].valueCode from Database
* input contains columnFamily 1..1
* input[columnFamily] ^short = "Column Family"
* input[columnFamily] ^definition = "The name of the column family to compact."
* input[columnFamily].type = CompactJobParameter#column-family
* input[columnFamily].value[x] only code
* input[columnFamily].valueCode from ColumnFamily
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the compaction took."
* output[processingDuration].type = CompactJobOutput#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"

Instance: CompactJobExample
InstanceOf: CompactJob
Description: "A compact job of the search param value index that is ready to be executed."
* status = #ready
* intent = #order
* code = JobType#compact "Compact a Database Column Family"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[database].type = CompactJobParameter#database
* input[database].valueCode = Database#index
* input[columnFamily].type = CompactJobParameter#column-family
* input[columnFamily].valueCode = ColumnFamily#search-param-value-index
