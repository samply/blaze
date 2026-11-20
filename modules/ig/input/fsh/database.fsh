CodeSystem: Database
Id: Database
Title: "Database"
Description: "The databases Blaze stores its data in."
* insert ShareableCodeSystem
* #index "Index"
* #transaction "Transaction"
* #resource "Resource"

ValueSet: Database
Id: Database
Title: "Database Value Set"
Description: "All databases Blaze stores its data in."
* insert ShareableValueSet
* include codes from system Database
