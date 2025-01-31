# Delete

The delete interaction removes an existing resource.

```
DELETE [base]/[type]/[id]
```

Because Blaze keeps a history of all resource versions, the version of the resource past the deletion will be still accessible in it's [history](history-instance.md). However past versions can be deleted themself via the [delete history](delete-history.md) interaction.

By default Blaze enforces referential integrity while deleting resources. So resources that are referred by other resources can't be deleted without deleting the other resources first. That behaviour can be changed by setting the [environment variable](../../deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

## Conditional Delete

The conditional delete operation allows to delete all resources matching certain criteria. The same search parameter as in the search type interaction can be used. The search is always strict, so it will fail on any unknown search parameter.

```
DELETE [base]/[type]?[search parameters]
```

By default, the delete is only performed if one resource matches. However it's possible to allow deleting multiple resources by setting the [environment variable](../../deployment/environment-variables.md) `ALLOW_MULTIPLE_DELETE` to `true`.

> [!NOTE]
> Due to stability concerns, there is a fix limit of 10,000 resources that can be deleted by this interaction. In case more than 10,000 resources match, an OperationOutcome with code `too-costly` is returned.

The successful response will have the status code `204 No Content` with no payload by default. However it's possible to specify a return preference of `OperationOutcome` by setting the `Prefer` header to `return=OperationOutcome`. In this case a success OperationOutcome with a diagnostic of the number of deleted resources is returned.

### Example

```json 
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "success",
      "code": "success",
      "diagnostics": "Successfully deleted 120 Provenances."
    }
  ]
}
```
