Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $JCS = https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus
Alias: $JO = https://samply.github.io/blaze/fhir/CodeSystem/JobOutput
Alias: $AJP = https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter
Alias: $AJO = https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobOutput
Alias: $BT = http://hl7.org/fhir/bundle-type
Alias: $HV = http://hl7.org/fhir/http-verb
Alias: $SEM = http://hl7.org/fhir/search-entry-mode
Alias: $LOINC = http://loinc.org
Alias: $REL = http://hl7.org/fhir/CodeSystem/iana-link-relations

CodeSystem: AsyncInteractionJobParameter
Id: AsyncInteractionJobParameter
Title: "Async Interaction Job Parameter"
* ^status = #active
* #bundle "Bundle"
* #t "Database Point in Time"

CodeSystem: AsyncInteractionJobOutput
Id: AsyncInteractionJobOutput
Title: "Async Interaction Job Output"
* ^status = #active
* #bundle "Bundle"
* #processing-duration "Processing Duration"

Profile: AsyncInteractionJob
Parent: Job
* code = $JT#async-interaction "Asynchronous Interaction Request"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains bundle 1..1
* input[bundle] ^short = "Bundle"
* input[bundle] ^definition = "A reference to the request bundle of the async interaction."
* input[bundle].type = $AJP#bundle
* input[bundle].value[x] only Reference (AsyncInteractionRequestBundle)
* input contains t 1..1
* input[t] ^short = "T"
* input[t] ^definition = "The database point in time to use for the execution of the handler."
* input[t].type = $AJP#t
* input[t].value[x] only unsignedInt
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains bundle 0..1
* output[bundle] ^short = "Bundle"
* output[bundle] ^definition = "A reference to the response bundle of the async interaction."
* output[bundle].type = $AJO#bundle
* output[bundle].value[x] only Reference (AsyncInteractionResponseBundle)
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the request processing took."
* output[processingDuration].type = $AJO#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"

Profile: AsyncInteractionRequestBundle
Parent: Bundle
* type = $BT#batch
* entry 1..*
* entry.request 1..1
* entry.search 0..0

Profile: AsyncInteractionResponseBundle
Parent: Bundle
* type = $BT#batch-response
* entry 1..*
* entry.response 1..1
* entry.search 0..0

Instance: AsyncInteractionJobSearchObservationReadyExample
InstanceOf: AsyncInteractionJob
* status = #ready
* intent = #order
* code = $JT#async-interaction "Asynchronous Interaction Request"
* authoredOn = "2024-05-20T15:25:20.927Z"
* input[bundle].type = $AJP#bundle "Bundle"
* input[bundle].valueReference.reference = "Bundle/foo"
* input[t].type = $AJP#t "Database Point in Time"
* input[t].valueUnsignedInt = 347856

Instance: AsyncInteractionJobSearchObservationRequestBundleExample
InstanceOf: AsyncInteractionRequestBundle
* type = $BT#batch
* entry[0].request.method = $HV#GET
* entry[0].request.url = "Observation?code=http://loinc.org|8310-5&_summary=count"

Instance: SearchObservationBundle
InstanceOf: Bundle
Usage: #inline
* type = $BT#searchset
* total = 1287
* link[0].relation = $REL#self
* link[0].url = "http://example.com"

Instance: AsyncInteractionJobSearchObservationResponseBundleExample
InstanceOf: AsyncInteractionResponseBundle
* type = $BT#batch-response
* entry[0].resource = SearchObservationBundle
* entry[0].response.status = "200"

Instance: AsyncInteractionJobSearchObservationCancellationRequestedExample
InstanceOf: AsyncInteractionJob
* status = #cancelled
* intent = #order
* businessStatus = $JCS#requested "Requested"
* code = $JT#async-interaction "Asynchronous Interaction Request"
* authoredOn = "2024-05-20T15:25:20.927Z"
* input[bundle].type = $AJP#bundle "Bundle"
* input[bundle].valueReference.reference = "Bundle/foo"
* input[t].type = $AJP#t "Database Point in Time"
* input[t].valueUnsignedInt = 347856
