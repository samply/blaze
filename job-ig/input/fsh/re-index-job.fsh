Alias: $JT = https://samply.github.io/blaze/fhir/CodeSystem/JobType
Alias: $RJP = https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter

CodeSystem: ReIndexJobParameter
Id: ReIndexJobParameter
* #search-param-url "Search Param URL"

Profile: ReIndexJob
Parent: Task
* code 1..1
* code = $JT#re-index "(Re)Index a Search Parameter"
* input ^slicing.discriminator.type = #pattern
* input ^slicing.discriminator.path = "type"
* input ^slicing.rules = #closed
* input contains searchParamUrl 1..1
* input[searchParamUrl] ^short = "Search Param URL"
* input[searchParamUrl] ^definition = "The URL of the Search Parameter to (re)index."
* input[searchParamUrl].type = $RJP#search-param-url
* input[searchParamUrl].value[x] only canonical

Instance: ReIndexJobExample
InstanceOf: ReIndexJob
* status = #ready
* intent = #order
* code = $JT#re-index "(Re)Index a Search Parameter"
* input[searchParamUrl].type = $RJP#search-param-url
* input[searchParamUrl].valueCanonical = "http://hl7.org/fhir/SearchParameter/Resource-profile"
