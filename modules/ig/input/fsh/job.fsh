CodeSystem: JobType
Id: JobType
Title: "Job Type"
Description: "The types of jobs Blaze executes."
* insert ShareableCodeSystem
* #async-interaction "Asynchronous Interaction Request"
* #async-bulk-data "Asynchronous Bulk Data Request"
* #compact "Compact a Database Column Family"
* #disk-perf "Measure Disk Performance"
* #re-index "(Re)Index a Search Parameter"

CodeSystem: JobStatusReason
Id: JobStatusReason
Title: "Job Status Reason"
Description: "The reasons for the status of a job."
* insert ShareableCodeSystem
* #orderly-shutdown "Orderly Shutdown"
* #paused "Paused"
* #resumed "Resumed"
* #incremented "Incremented"
* #started "Started"

CodeSystem: JobCancelledSubStatus
Id: JobCancelledSubStatus
Title: "Job Cancelled Sub Status"
Description: "The sub status of a cancelled job, telling whether the cancellation was only requested or is already finished."
* insert ShareableCodeSystem
* #requested "Requested"
* #finished "Finished"

CodeSystem: JobOutput
Id: JobOutput
Title: "Job Output"
Description: "Types of the outputs all jobs share."
* insert ShareableCodeSystem
* #error-category "Error Category"
* #error "Error"

CodeSystem: ErrorCategory
Id: ErrorCategory
Title: "Error Category"
Description: "The categories of the error a failed job reports."
* insert ShareableCodeSystem
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
Description: "All categories of the error a failed job reports."
* insert ShareableValueSet
* include codes from system ErrorCategory

Profile: Job
Parent: Task
Description: "The base profile of all jobs Blaze executes. A job is a Task with a type, the time it was authored and, if it failed, an error."
* obeys status-reason-in-progress
* obeys status-reason-on-hold
* identifier ^slicing.discriminator.type = #pattern
* identifier ^slicing.discriminator.path = "system"
* identifier ^slicing.rules = #open
* identifier contains jobNumber 0..1
* identifier[jobNumber] ^short = "Job Number"
* identifier[jobNumber].system = "https://blaze-server.org/fhir/sid/JobNumber"
* statusReason.coding ^slicing.discriminator.type = #pattern
* statusReason.coding ^slicing.discriminator.path = "system"
* statusReason.coding ^slicing.rules = #open
* statusReason.coding contains jobStatusReason 0..1
* statusReason.coding[jobStatusReason] ^short = "Job Status Reason"
* statusReason.coding[jobStatusReason].system = "https://blaze-server.org/fhir/CodeSystem/JobStatusReason"
* businessStatus.coding ^slicing.discriminator.type = #pattern
* businessStatus.coding ^slicing.discriminator.path = "system"
* businessStatus.coding ^slicing.rules = #open
* businessStatus.coding contains jobCancelledSubStatus 0..1
* businessStatus.coding[jobCancelledSubStatus] ^short = "Job Cancelled Sub Status"
* businessStatus.coding[jobCancelledSubStatus].system = "https://blaze-server.org/fhir/CodeSystem/JobCancelledSubStatus"
* code 1..1
* authoredOn 1..1
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains errorCategory 0..1
* output[errorCategory] ^short = "Error Category"
* output[errorCategory] ^definition = "Error category."
* output[errorCategory].type = JobOutput#error-category
* output[errorCategory].value[x] only code
* output[errorCategory].valueCode from ErrorCategory
* output contains error 0..1
* output[error] ^short = "Error"
* output[error] ^definition = "Error message."
* output[error].type = JobOutput#error
* output[error].value[x] only string

Invariant: status-reason-in-progress
Description: "Assigns possible reasons to the 'in-progress' status."
Severity: #error
Expression: "status = 'in-progress' implies statusReason.coding.where(system = 'https://blaze-server.org/fhir/CodeSystem/JobStatusReason' and (code = 'started' or code = 'incremented' or code = 'resumed'))"

Invariant: status-reason-on-hold
Description: "Assigns possible reasons to the 'on-hold' status."
Severity: #error
Expression: "status = 'on-hold' implies statusReason.coding.where(system = 'https://blaze-server.org/fhir/CodeSystem/JobStatusReason' and (code = 'paused' or code = 'orderly-shutdown'))"

Invariant: sub-status-cancelled
Description: "Assigns possible reasons to the 'on-hold' status."
Severity: #error
Expression: "status = 'cancelled' implies businessStatus.coding.where(system = 'https://blaze-server.org/fhir/CodeSystem/JobCancelledSubStatus' and (code = 'requested' or code = 'finished'))"
