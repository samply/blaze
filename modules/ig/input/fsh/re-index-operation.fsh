Instance: re-index
InstanceOf: OperationDefinition
Usage: #definition
* url = "https://blaze-server.org/fhir/OperationDefinition/re-index"
* name = "ReIndex"
* title = "(Re)Index a Search Parameter"
* status = #active
* kind = #operation
* description = "(Re)indexes all resources of the search parameter with the given URL. The response is always async according to the Asynchronous Interaction Request Pattern. The progress is available as outputs of the underlying ReIndexJob Task resource."
* affectsState = true
* code = #re-index
* system = true
* type = false
* instance = false
* parameter[+]
  * name = #search-param-url
  * use = #in
  * min = 1
  * max = "1"
  * documentation = "The URL of the search parameter to (re)index. The search parameter has to be known to the server. A versioned canonical is not supported."
  * type = #canonical
  * targetProfile = "http://hl7.org/fhir/StructureDefinition/SearchParameter"
