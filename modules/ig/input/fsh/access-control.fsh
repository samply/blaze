CodeSystem: AccessControl
Id: AccessControl
Title: "Access Control"
Description: "Codes Blaze labels the resources it owns itself with, in order to protect them against changes by clients. The codes belong to Blaze alone. Clients can't create or update resources carrying them."
* ^status = #active
* ^caseSensitive = true
* #read-only "Read-Only" "The resource can't be updated or deleted by clients. Carried as tag in Resource.meta.tag."
* #internal "Internal" "The resource is owned by Blaze. Clients can't update or delete it and can't delete any of its versions. Carried as security label in Resource.meta.security."
