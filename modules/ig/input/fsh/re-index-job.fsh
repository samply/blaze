Alias: UCUM = http://unitsofmeasure.org

CodeSystem: ReIndexJobParameter
Id: ReIndexJobParameter
Title: "(Re)Index Job Parameter"
* ^status = #active
* #search-param-url "Search Param URL"

CodeSystem: ReIndexJobOutput
Id: ReIndexJobOutput
Title: "(Re)Index Job Output"
* ^status = #active
* #total-resources "Total Resources"
* #resources-processed "Resources Processed"
* #processing-duration "Processing Duration"
* #next-resource "Next Resource"

Profile: ReIndexJob
Parent: Job
* code = JobType#re-index "(Re)Index a Search Parameter"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #open
* input contains searchParamUrl 1..1
* input[searchParamUrl] ^short = "Search Param URL"
* input[searchParamUrl] ^definition = "The URL of the Search Parameter to (re)index."
* input[searchParamUrl].type = ReIndexJobParameter#search-param-url
* input[searchParamUrl].value[x] only canonical
* output ^slicing.discriminator.type = #pattern
* output ^slicing.discriminator.path = "type"
* output ^slicing.rules = #open
* output contains totalResources 0..1
* output[totalResources] ^short = "Total Resources"
* output[totalResources] ^definition = "Total number of resources to (re)index."
* output[totalResources].type = ReIndexJobOutput#total-resources
* output[totalResources].value[x] only unsignedInt
* output contains resourcesProcessed 0..1
* output[resourcesProcessed] ^short = "Resources Processed"
* output[resourcesProcessed] ^definition = "Number of resources currently processed."
* output[resourcesProcessed].type = ReIndexJobOutput#resources-processed
* output[resourcesProcessed].value[x] only unsignedInt
* output contains processingDuration 0..1
* output[processingDuration] ^short = "Processing Duration"
* output[processingDuration] ^definition = "Duration the (re)indexing processing took. Durations while the job was paused don't count."
* output[processingDuration].type = ReIndexJobOutput#processing-duration
* output[processingDuration].value[x] only Quantity
* output[processingDuration].valueQuantity
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"
* output contains nextResource 0..1
* output[nextResource] ^short = "Next Resource"
* output[nextResource] ^definition = "The literal reference of the resource to continue with. Used in case the job is resumed after manual pausing or shutdown of Blaze."
* output[nextResource].type = ReIndexJobOutput#next-resource
* output[nextResource].value[x] only string

Instance: ReIndexJobReadyExample
InstanceOf: ReIndexJob
* status = #ready
* intent = #order
* code = JobType#re-index "(Re)Index a Search Parameter"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[searchParamUrl].type = ReIndexJobParameter#search-param-url "Search Param URL"
* input[searchParamUrl].valueCanonical = "http://hl7.org/fhir/SearchParameter/Resource-profile"

Instance: ReIndexJobInProgressExample
InstanceOf: ReIndexJob
* status = #in-progress
* statusReason = JobStatusReason#started "Started"
* intent = #order
* code = JobType#re-index "(Re)Index a Search Parameter"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[searchParamUrl].type = ReIndexJobParameter#search-param-url "Search Param URL"
* input[searchParamUrl].valueCanonical = "http://hl7.org/fhir/SearchParameter/Resource-profile"
* output[totalResources].type = ReIndexJobOutput#total-resources "Total Resources"
* output[totalResources].valueUnsignedInt = 1000
* output[resourcesProcessed].type = ReIndexJobOutput#resources-processed "Resources Processed"
* output[resourcesProcessed].valueUnsignedInt = 100
* output[processingDuration].type = ReIndexJobOutput#processing-duration "Processing Duration"
* output[processingDuration].valueQuantity.value = 10

Instance: ReIndexJobFailedExample
InstanceOf: ReIndexJob
* status = #failed
* intent = #order
* code = JobType#re-index "(Re)Index a Search Parameter"
* authoredOn = "2024-04-13T10:05:20.927Z"
* input[searchParamUrl].type = ReIndexJobParameter#search-param-url "Search Param URL"
* input[searchParamUrl].valueCanonical = "http://hl7.org/fhir/SearchParameter/Resource-profile"
* output[error].type = JobOutput#error "Error"
* output[error].valueString = "error message"
