Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $JO = https://samply.github.io/blaze/fhir/CodeSystem/JobOutput

CodeSystem: JobOutput
Id: JobOutput
Title: "Job Output"
* #error "Error"

Profile: Job
Parent: Task
* identifier ^slicing.discriminator.type = #pattern
* identifier ^slicing.discriminator.path = "system"
* identifier ^slicing.rules = #closed
* identifier contains jobNumber 0..1
* identifier[jobNumber] ^short = "Job Number"
* identifier[jobNumber].system = "https://samply.github.io/blaze/fhir/sid/JobNumber"
* code 1..1
* authoredOn 1..1
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #closed
* output contains error 0..1
* output[error] ^short = "Error"
* output[error] ^definition = "Error message."
* output[error].type = $JO#error
* output[error].value[x] only string
