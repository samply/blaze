Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $CJP = https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter

CodeSystem: CompactJobParameter
Id: CompactJobParameter
* #column-family-name "Column Family Name"

Profile: CompactJob
Parent: Job
* code = $JT#compact "Compact Database Column Families"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #closed
* input contains columnFamilyName 1..1
* input[columnFamilyName] ^short = "Column Family Name"
* input[columnFamilyName] ^definition = "The name of the column family to compact."
* input[columnFamilyName].type = $CJP#column-family-name
* input[columnFamilyName].value[x] only string

Instance: CompactJobExample
InstanceOf: CompactJob
* status = #ready
* intent = #order
* code = $JT#compact "Compact Database Column Families"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[columnFamilyName].type = $CJP#column-family-name
* input[columnFamilyName].valueString = "SearchParamValueIndex"
