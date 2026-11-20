Instance: TaskInput
InstanceOf: SearchParameter
Usage: #definition
* name = "TaskInput"
* status = #active
* description = "Reference to inputs of a task."
* code = #input
* base = #Task
* type = #reference
* expression = "Task.input.value.ofType(Reference)"

Instance: TaskOutput
InstanceOf: SearchParameter
Usage: #definition
* name = "TaskOutput"
* status = #active
* description = "Reference to outputs of a task."
* code = #output
* base = #Task
* type = #reference
* expression = "Task.output.value.ofType(Reference)"

Instance: JobSearchParameterBundle
InstanceOf: Bundle
Description: "The search parameters Blaze adds to Task in order to search jobs by the resources their inputs and outputs reference."
* type = #collection
* entry[0].fullUrl = "https://blaze-server.org/fhir/SearchParameter/TaskInput"
* entry[0].resource = TaskInput
* entry[1].fullUrl = "https://blaze-server.org/fhir/SearchParameter/TaskOutput"
* entry[1].resource = TaskOutput
