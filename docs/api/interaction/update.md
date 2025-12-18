# Update

The update interaction creates a new current version for an existing resource or creates an initial version if no resource already exists for the given id.

```
PUT [base]/[type]/[id]
```

Blaze keeps track over the history of all updates of each resource. However if the content of the resource update is equal to the current version of the resource, no new history entry is created. Usually such identical content updates will only cost a very small amount of transaction handling storage but no additional resource or index storage.

## Read-Only Resources

Some resources like base FHIR StructureDefinition resources are read-only and can't be updated. That resources contain a tag with the system `https://samply.github.io/blaze/fhir/CodeSystem/AccessControl` and code `read-only`.

## Conditional Update

Conditional update interaction will be implemented in the future. Please see issue [#361](https://github.com/samply/blaze/issues/361) for more information.

## Validation Support

See [Validation](../../validation.md).
