# Delete

The delete interaction removes an existing resource.

```
DELETE [base]/[type]/[id]
```

Because Blaze keeps a history of all resource versions, the version of the resource past the deletion will be still accessible in it's [history](history-instance.md). However past versions can be deleted themself via the [delete history](delete-history.md) interaction.

By default, Blaze enforces referential integrity while deleting resources. So resources that are referred by other resources can't be deleted without deleting the other resources first. That behaviour can be changed by setting the [environment variable](../../deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

## Read-Only Resources

Some resources like base FHIR StructureDefinition resources are read-only and can't be deleted. That resources contain a tag with the system `https://samply.github.io/blaze/fhir/CodeSystem/AccessControl` and code `read-only`.
