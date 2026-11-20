Alias: $BT = http://hl7.org/fhir/bundle-type
Alias: $HV = http://hl7.org/fhir/http-verb
Alias: $SEM = http://hl7.org/fhir/search-entry-mode
Alias: $LOINC = http://loinc.org

CodeSystem: AsyncInteractionJobParameter
Id: AsyncInteractionJobParameter
Title: "Async Interaction Job Parameter"
Description: "Types of the inputs of an asynchronous interaction job."
* insert ShareableCodeSystem
* #bundle "Bundle"
* #t "Database Point in Time"

CodeSystem: AsyncInteractionJobOutput
Id: AsyncInteractionJobOutput
Title: "Async Interaction Job Output"
Description: "Types of the outputs of an asynchronous interaction job."
* insert ShareableCodeSystem
* #bundle "Bundle"
* #processing-duration "Processing Duration"

Extension: ReturnPreference
Id: return-preference
Title: "Return Preference"
Description: "The return preference of the `Prefer` header of an asynchronous request, one of `minimal`, `representation` and `OperationOutcome`. The request is executed later, without its HTTP headers, so the preference is kept with the request in the request bundle."
* ^status = #active
* ^context[0].type = #element
* ^context[0].expression = "Bundle.entry.request"
* value[x] only code

Profile: AsyncInteractionJob
Parent: Job
Description: "A job executing a request of the Asynchronous Interaction Request Pattern. Its input references the request bundle and its output the response bundle."
* code = JobType#async-interaction "Asynchronous Interaction Request"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains bundle 1..1
* input[bundle] ^short = "Bundle"
* input[bundle] ^definition = "A reference to the request bundle of the async interaction."
* input[bundle].type = AsyncInteractionJobParameter#bundle
* input[bundle].value[x] only Reference (AsyncInteractionRequestBundle)
* input contains t 1..1
* input[t] ^short = "T"
* input[t] ^definition = "The database point in time to use for the execution of the handler."
* input[t].type = AsyncInteractionJobParameter#t
* input[t].value[x] only unsignedInt
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains bundle 0..1
* output[bundle] ^short = "Bundle"
* output[bundle] ^definition = "A reference to the response bundle of the async interaction."
* output[bundle].type = AsyncInteractionJobOutput#bundle
* output[bundle].value[x] only Reference (AsyncInteractionResponseBundle)
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the request processing took."
* output[processingDuration].type = AsyncInteractionJobOutput#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"

Profile: AsyncInteractionRequestBundle
Parent: Bundle
Description: "The batch bundle holding the request of an asynchronous interaction job."
* type = $BT#batch
* entry 1..*
* entry.request 1..1
* entry.request.extension contains ReturnPreference named returnPreference 0..1
* entry.search 0..0

Profile: AsyncInteractionResponseBundle
Parent: Bundle
Description: "The batch-response bundle holding the response of an asynchronous interaction job."
* type = $BT#batch-response
* entry 1..*
* entry.response 1..1
* entry.search 0..0

Instance: AsyncInteractionJobSearchObservationReadyExample
InstanceOf: AsyncInteractionJob
Description: "An asynchronous interaction job counting Observations that is ready to be executed."
* status = #ready
* intent = #order
* code = JobType#async-interaction "Asynchronous Interaction Request"
* authoredOn = "2024-05-20T15:25:20.927Z"
* input[bundle].type = AsyncInteractionJobParameter#bundle "Bundle"
* input[bundle].valueReference.reference = "Bundle/AsyncInteractionJobSearchObservationRequestBundleExample"
* input[t].type = AsyncInteractionJobParameter#t "Database Point in Time"
* input[t].valueUnsignedInt = 347856

Instance: AsyncInteractionJobSearchObservationRequestBundleExample
InstanceOf: AsyncInteractionRequestBundle
Description: "The request bundle of an asynchronous interaction job counting Observations."
* type = $BT#batch
* entry.request.method = $HV#GET
* entry.request.url = "Observation?code=http://loinc.org|8310-5&_summary=count"

Instance: SearchObservationBundle
InstanceOf: Bundle
Usage: #inline
* type = $BT#searchset
* total = 1287

Instance: AsyncInteractionJobSearchObservationResponseBundleExample
InstanceOf: AsyncInteractionResponseBundle
Description: "The response bundle of an asynchronous interaction job counting Observations."
* type = $BT#batch-response
* entry.resource = SearchObservationBundle
* entry.response.status = "200"

Instance: AsyncInteractionJobSearchObservationCancellationRequestedExample
InstanceOf: AsyncInteractionJob
Description: "An asynchronous interaction job counting Observations whose cancellation was requested."
* status = #cancelled
* intent = #order
* businessStatus = JobCancelledSubStatus#requested "Requested"
* code = JobType#async-interaction "Asynchronous Interaction Request"
* authoredOn = "2024-05-20T15:25:20.927Z"
* input[bundle].type = AsyncInteractionJobParameter#bundle "Bundle"
* input[bundle].valueReference.reference = "Bundle/AsyncInteractionJobSearchObservationRequestBundleExample"
* input[t].type = AsyncInteractionJobParameter#t "Database Point in Time"
* input[t].valueUnsignedInt = 347856
