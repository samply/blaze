# Delete

The delete interaction removes an existing resource.

```
DELETE [base]/[type]/[id]
```

> [!NOTE]
> Blaze runs all writes as transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction blocks all smaller ones — including this delete — until it completes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

Because Blaze keeps a history of all resource versions, the version of the resource past the deletion will be still accessible in it's [history](history-instance.md). However past versions can be deleted themself via the [delete history](delete-history.md) interaction.

By default, Blaze enforces referential integrity while deleting resources. So resources that are referred by other resources can't be deleted without deleting the other resources first. That behaviour can be changed by setting the [environment variable](../../deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

## Read-Only Resources

Some resources like base FHIR StructureDefinition resources are read-only and can't be deleted. That resources contain two tags with code `read-only` — one with the system `https://blaze-server.org/fhir/CodeSystem/AccessControl` and one with the legacy system `https://samply.github.io/blaze/fhir/CodeSystem/AccessControl`. Blaze emits both so that downgrading to an older Blaze version, which enforces read-only via the legacy tag, keeps these resources protected; the legacy tag will be removed in the next major version (v2).
