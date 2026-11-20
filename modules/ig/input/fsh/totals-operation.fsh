Instance: totals
InstanceOf: OperationDefinition
Usage: #definition
* url = "https://blaze-server.org/fhir/OperationDefinition/totals"
* name = "Totals"
* title = "Resource Totals"
* status = #active
* experimental = true
* kind = #operation
* description = "The operation is experimental. It may change in a future version without prior deprecation. Retrieves the total number of resources by resource type. The response is a Parameters resource with one parameter per resource type with at least one resource. The name of the parameter is the resource type and its valueUnsignedInt is the number of resources of that type. Because the names of the parameters are the resource types, the parameters aren't declared individually."
* affectsState = false
* code = #totals
* system = true
* type = false
* instance = false
