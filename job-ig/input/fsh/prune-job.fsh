Alias: UCUM = http://unitsofmeasure.org
Alias: $FT = http://hl7.org/fhir/fhir-types
Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $JSR = https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason
Alias: $JO = https://samply.github.io/blaze/fhir/CodeSystem/JobOutput
Alias: $PJP = https://samply.github.io/blaze/fhir/CodeSystem/PruneJobParameter
Alias: $PJO = https://samply.github.io/blaze/fhir/CodeSystem/PruneJobOutput
Alias: $PI = https://samply.github.io/blaze/fhir/CodeSystem/PruneIndices

CodeSystem: PruneJobParameter
Id: PruneJobParameter
Title: "Prune Job Parameter"
* ^status = #active
* #t "T"

CodeSystem: PruneJobOutput
Id: PruneJobOutput
Title: "Prune Job Output"
* ^status = #active
* #total-index-entries "Total Index Entries"
* #index-entries-processed "Index Entries Processed"
* #index-entries-deleted "Index Entries Deleted"
* #processing-duration "Processing Duration"
* #next-index "Next Index"
* #next-type "Next Type"
* #next-id "Next Id"
* #next-t "Next T"

CodeSystem: PruneIndices
Id: PruneIndices
Title: "Prune Indices"
* ^status = #active
* #resource-as-of "ResourceAsOf"
* #type-as-of "TypeAsOf"
* #system-as-of "SystemAsOf"

ValueSet: PruneIndices
Id: PruneIndices
Title: "Prune Indices Value Set"
* ^status = #active
* include codes from system PruneIndices

Profile: PruneJob
Parent: Job
* code = $JT#prune "Prune the Database"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains t 1..1
* input[t] ^short = "T"
* input[t] ^definition = "The database point in time to use as start of pruning."
* input[t].type = $PJP#t
* input[t].value[x] only positiveInt
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains totalIndexEntries 0..1
* output[totalIndexEntries] ^short = "Total Index Entries"
* output[totalIndexEntries] ^definition = "Estimated total number of index entries to prune."
* output[totalIndexEntries].type = $PJO#total-index-entries
* output[totalIndexEntries].value[x] only unsignedInt
* output contains indexEntriesProcessed 0..1
* output[indexEntriesProcessed] ^short = "Index Entries Processed"
* output[indexEntriesProcessed] ^definition = "Number of index entries processed."
* output[indexEntriesProcessed].type = $PJO#index-entries-processed
* output[indexEntriesProcessed].value[x] only unsignedInt
* output contains indexEntriesDeleted 0..1
* output[indexEntriesDeleted] ^short = "Index Entries Deleted"
* output[indexEntriesDeleted] ^definition = "Number of index entries deleted."
* output[indexEntriesDeleted].type = $PJO#index-entries-deleted
* output[indexEntriesDeleted].value[x] only unsignedInt
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the pruning processing took. Durations while the job was paused don't count."
* output[processingDuration].type = $PJO#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"
* output contains nextIndex 0..1
* output[nextIndex] ^short = "Next Index"
* output[nextIndex] ^definition = "The name of the index to continue with. Used in case the job is resumed after manual pausing or shutdown of Blaze."
* output[nextIndex].type = $PJO#next-index
* output[nextIndex].value[x] only code
* output[nextIndex].valueCode from PruneIndices
* output contains nextType 0..1
* output[nextType] ^short = "Next Type"
* output[nextType] ^definition = "The FHIR resource type to continue with. Used in case the job is resumed after manual pausing or shutdown of Blaze."
* output[nextType].type = $PJO#next-type
* output[nextType].value[x] only code
* output[nextType].valueCode from http://hl7.org/fhir/ValueSet/resource-types
* output contains nextId 0..1
* output[nextId] ^short = "Next Id"
* output[nextId] ^definition = "The FHIR resource id to continue with. Used in case the job is resumed after manual pausing or shutdown of Blaze."
* output[nextId].type = $PJO#next-id
* output[nextId].value[x] only id
* output contains nextT 0..1
* output[nextT] ^short = "Next T"
* output[nextT] ^definition = "The database point in time to continue with. Used in case the job is resumed after manual pausing or shutdown of Blaze."
* output[nextT].type = $PJO#next-t
* output[nextT].value[x] only positiveInt

Instance: PruneJobReadyExample
InstanceOf: PruneJob
* status = #ready
* intent = #order
* code = $JT#prune "Prune the Database"
* authoredOn = "2024-10-15T15:01:00.000Z"
* input[t].type = $PJP#t "T"
* input[t].valuePositiveInt = 42

Instance: PruneJobInProgressExample
InstanceOf: PruneJob
* status = #in-progress
* statusReason = $JSR#started "Started"
* intent = #order
* code = $JT#prune "Prune the Database"
* authoredOn = "2024-10-15T15:01:00.000Z"
* input[t].type = $PJP#t "T"
* input[t].valuePositiveInt = 42
* output[totalIndexEntries].type = $PJO#total-index-entries "Total Index Entries"
* output[totalIndexEntries].valueUnsignedInt = 1000
* output[indexEntriesProcessed].type = $PJO#index-entries-processed "Index Entries Processed"
* output[indexEntriesProcessed].valueUnsignedInt = 100
* output[indexEntriesDeleted].type = $PJO#index-entries-deleted "Index Entries Deleted"
* output[indexEntriesDeleted].valueUnsignedInt = 10
* output[processingDuration].type = $PJO#processing-duration "Processing Duration"
* output[processingDuration].valueQuantity.value = 10
* output[nextIndex].type = $PJO#next-index "Next Index"
* output[nextIndex].valueCode = $PI#resource-as-of
* output[nextType].type = $PJO#next-type "Next Type"
* output[nextType].valueCode = $FT#Patient
* output[nextId].type = $PJO#next-id "Next Id"
* output[nextId].valueId = "0"
* output[nextT].type = $PJO#next-t "Next T"
* output[nextT].valuePositiveInt = 23

Instance: PruneJobFailedExample
InstanceOf: PruneJob
* status = #failed
* intent = #order
* code = $JT#prune "Prune the Database"
* authoredOn = "2024-10-15T15:01:00.000Z"
* input[t].type = $PJP#t "T"
* input[t].valuePositiveInt = 42
* output[error].type = $JO#error "Error"
* output[error].valueString = "error message"
