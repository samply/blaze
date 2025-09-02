Alias: $PM = http://hl7.org/fhir/search-processingmode

Instance: TaskInput
InstanceOf: SearchParameter
Usage: #definition
* name = "input"
* status = #active
* description = "Reference to inputs of a task."
* code = #input
* base = #Task
* type = #reference
* expression = "Task.input.value.ofType(Reference)"
* processingMode = $PM#normal

Instance: TaskOutput
InstanceOf: SearchParameter
Usage: #definition
* name = "output"
* status = #active
* description = "Reference to outputs of a task."
* code = #output
* base = #Task
* type = #reference
* expression = "Task.output.value.ofType(Reference)"
* processingMode = $PM#normal

Instance: JobSearchParameterBundle
InstanceOf: Bundle
* type = #collection
* entry[0].fullUrl = "https://samply.github.io/blaze/fhir/SearchParameter/TaskInput"
* entry[0].resource = TaskInput
* entry[1].fullUrl = "https://samply.github.io/blaze/fhir/SearchParameter/TaskOutput"
* entry[1].resource = TaskOutput
