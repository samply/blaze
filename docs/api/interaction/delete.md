# Delete

The delete interaction removes an existing resource.

```
DELETE [base]/[type]/[id]
```

> [!NOTE]
> Blaze runs all writes as transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction blocks all smaller ones — including this delete — until it completes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

On success, Blaze returns a `204 No Content` without a body. The response contains an `ETag` header with the version id and a `Last-Modified` header with the transaction time of the deletion:

```
HTTP/1.1 204 No Content
Last-Modified: Tue, 24 Jun 2025 09:03:22 GMT
ETag: W/"23"
```

The delete interaction is idempotent: deleting a resource that doesn't exist or was already deleted also returns a `204 No Content`.

Because Blaze keeps a history of all resource versions, the version of the resource past the deletion will be still accessible in it's [history](history-instance.md). However past versions can be deleted themself via the [delete history](delete-history.md) interaction. A [read](read.md) of a deleted resource returns a `410 Gone`.

By default, Blaze enforces referential integrity while deleting resources. So resources that are referred by other resources can't be deleted without deleting the other resources first. In that case Blaze returns a `409 Conflict` with an `OperationOutcome`. That behaviour can be changed by setting the [environment variable](../../deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

## Read-Only Resources

Some resources like base FHIR StructureDefinition resources are read-only and can't be deleted. That resources contain two tags with code `read-only` — one with the system `https://blaze-server.org/fhir/CodeSystem/AccessControl` and one with the legacy system `https://samply.github.io/blaze/fhir/CodeSystem/AccessControl`. Blaze emits both so that downgrading to an older Blaze version, which enforces read-only via the legacy tag, keeps these resources protected; the legacy tag will be removed in the next major version (v2).
