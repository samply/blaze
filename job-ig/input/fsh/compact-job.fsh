Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $CJP = https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter
Alias: $CJO = https://samply.github.io/blaze/fhir/CodeSystem/CompactJobOutput
Alias: $DB = https://samply.github.io/blaze/fhir/CodeSystem/Database
Alias: $CF = https://samply.github.io/blaze/fhir/CodeSystem/ColumnFamily

CodeSystem: CompactJobParameter
Id: CompactJobParameter
Title: "Compact Job Parameter"
* ^status = #active
* #database "Database"
* #column-family "Column Family"

CodeSystem: CompactJobOutput
Id: CompactJobOutput
Title: "Compact Job Output"
* ^status = #active
* #processing-duration "Processing Duration"

Profile: CompactJob
Parent: Job
* code = $JT#compact "Compact a Database Column Family"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains database 1..1
* input[database] ^short = "Database"
* input[database] ^definition = "The name of the database to compact."
* input[database].type = $CJP#database
* input[database].value[x] only code
* input[database].valueCode from Database
* input contains columnFamily 1..1
* input[columnFamily] ^short = "Column Family"
* input[columnFamily] ^definition = "The name of the column family to compact."
* input[columnFamily].type = $CJP#column-family
* input[columnFamily].value[x] only code
* input[columnFamily].valueCode from ColumnFamily
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the compaction took."
* output[processingDuration].type = $CJO#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"

Instance: CompactJobExample
InstanceOf: CompactJob
* status = #ready
* intent = #order
* code = $JT#compact "Compact a Database Column Family"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[database].type = $CJP#database
* input[database].valueCode = $DB#index
* input[columnFamily].type = $CJP#column-family
* input[columnFamily].valueCode = $CF#search-param-value-index
