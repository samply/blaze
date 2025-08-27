Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $JSR = https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason
Alias: $JCS = https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus
Alias: $JO = https://samply.github.io/blaze/fhir/CodeSystem/JobOutput

CodeSystem: JobType
Id: JobType
Title: "Job Type"
* ^status = #active
* #async-interaction "Asynchronous Interaction Request"
* #async-bulk-data "Asynchronous Bulk Data Request"
* #compact "Compact a Database Column Family"
* #re-index "(Re)Index a Search Parameter"

CodeSystem: JobStatusReason
Id: JobStatusReason
Title: "Job Status Reason"
* ^status = #active
* #orderly-shutdown "Orderly Shutdown"
* #paused "Paused"
* #resumed "Resumed"
* #incremented "Incremented"
* #started "Started"

CodeSystem: JobCancelledSubStatus
Id: JobCancelledSubStatus
Title: "Job Cancelled Sub Status"
* ^status = #active
* #requested "Requested"
* #finished "Finished"

CodeSystem: JobOutput
Id: JobOutput
Title: "Job Output"
* ^status = #active
* #error-category "Error Category"
* #error "Error"

CodeSystem: ErrorCategory
Id: ErrorCategory
Title: "Error Category"
* ^status = #active
* #unavailable
* #interrupted
* #busy
* #incorrect
* #forbidden
* #unsupported
* #not-found
* #conflict
* #fault

ValueSet: ErrorCategory
Id: ErrorCategory
Title: "Error Category Value Set"
* ^status = #active
* include codes from system ErrorCategory

Profile: Job
Parent: Task
* obeys status-reason-in-progress
* obeys status-reason-on-hold
* identifier ^slicing.discriminator.type = #pattern
* identifier ^slicing.discriminator.path = "system"
* identifier ^slicing.rules = #open
* identifier contains jobNumber 0..1
* identifier[jobNumber] ^short = "Job Number"
* identifier[jobNumber].system = "https://samply.github.io/blaze/fhir/sid/JobNumber"
* statusReason.concept.coding ^slicing.discriminator.type = #pattern
* statusReason.concept.coding ^slicing.discriminator.path = "system"
* statusReason.concept.coding ^slicing.rules = #open
* statusReason.concept.coding contains jobStatusReason 0..1
* statusReason.concept.coding[jobStatusReason] ^short = "Job Status Reason"
* statusReason.concept.coding[jobStatusReason].system = "https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason"
* businessStatus.coding ^slicing.discriminator.type = #pattern
* businessStatus.coding ^slicing.discriminator.path = "system"
* businessStatus.coding ^slicing.rules = #open
* businessStatus.coding contains jobCancelledSubStatus 0..1
* businessStatus.coding[jobCancelledSubStatus] ^short = "Job Cancelled Sub Status"
* businessStatus.coding[jobCancelledSubStatus].system = "https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus"
* code 1..1
* intent = #order
* authoredOn 1..1
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains errorCategory 0..1
* output[errorCategory] ^short = "Error Category"
* output[errorCategory] ^definition = "Error category."
* output[errorCategory].type = $JO#error-category
* output[errorCategory].value[x] only code
* output[errorCategory].valueCode from ErrorCategory
* output contains error 0..1
* output[error] ^short = "Error"
* output[error] ^definition = "Error message."
* output[error].type = $JO#error
* output[error].value[x] only string

Invariant: status-reason-in-progress
Description: "Assigns possible reasons to the 'in-progress' status."
Severity: #error
Expression: "status = 'in-progress' implies statusReason.concept.coding.where(system = 'https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason' and (code = 'started' or code = 'incremented' or code = 'resumed'))"

Invariant: status-reason-on-hold
Description: "Assigns possible reasons to the 'on-hold' status."
Severity: #error
Expression: "status = 'on-hold' implies statusReason.concept.coding.where(system = 'https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason' and (code = 'paused' or code = 'orderly-shutdown'))"

Invariant: sub-status-cancelled
Description: "Assigns possible reasons to the 'on-hold' status."
Severity: #error
Expression: "status = 'cancelled' implies businessStatus.coding.where(system = 'https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus' and (code = 'requested' or code = 'finished'))"
