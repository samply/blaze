CodeSystem: ValidationStatus
Id: ValidationStatus
Title: "Validation Status"
* ^status = #active
* #invalid "Invalid"

Extension: ValidationOutcome
Id: validation-outcome
Title: "Validation Outcome"
Description: "References the contained OperationOutcome produced by the external validation of a resource. The referenced resource's id is opaque and not fixed; follow this reference to locate the OperationOutcome instead of assuming a well-known id."
* ^status = #active
* ^context[0].type = #element
* ^context[0].expression = "Meta"
* value[x] only Reference(OperationOutcome)
