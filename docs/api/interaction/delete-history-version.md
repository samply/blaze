# Delete History Version <Badge type="info" text="Feature: INTERACTION_DELETE_HISTORY_VERSION"/> <Badge type="warning" text="Since 1.14.0"/>

> [!CAUTION]
> The delete history version interaction is currently **alpha** and has to be enabled explicitly by setting the env var `ENABLE_INTERACTION_DELETE_HISTORY_VERSION` to true. Please don't use it in production. Please be aware that the database might not be able to migrate to a newer version of Blaze if the operation was used.

> [!CAUTION]
> The delete history version interaction is trial use in the unreleased next version of FHIR. So it is subject to change. Please use it with care.

The delete history version interaction removes a single historical version of a resource. The current version can't be removed by this interaction.

```
DELETE [base]/[type]/[id]/_history/[vid]
```

> [!NOTE]
> Blaze runs all writes as transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction blocks all smaller ones — including this delete history version — until it completes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

On success, Blaze returns a `204 No Content` without a body.

Deleting a historical version of a resource means that this version can no longer be accessed. Subsequent [versioned reads](vread.md) of that version will return a `404 Not Found`. The version is also removed from the [instance history](history-instance.md), the [type history](history-type.md) and the [system history](history-system.md). All other versions of the resource stay accessible. Only active [paging sessions](../../api.md#paging-sessions) and [asynchronous requests](../../api.md#asynchronous-requests) started before the delete history version interaction will be able to access the deleted version for a limited amount of time.

The delete history version interaction is idempotent: deleting a version that doesn't exist or was already deleted has no effect and also returns a `204 No Content`. The same applies to a resource that doesn't exist at all.

All versions of a resource except the current version can be deleted at once via the [delete history](delete-history.md) interaction.

The delete history version interaction can also be used in [batches](batch.md) and [transactions](transaction.md). A transaction can delete several versions of the same resource, but each version only once, and can't combine that with other actions on the resource. Deleting the current version fails the whole transaction with a `409 Conflict`. Its response entry carries the status `204` without an `ETag`, because deleting a version doesn't create a new one.

## Internal Resources

Resources Blaze owns itself can carry the security label `internal` with the system `https://blaze-server.org/fhir/CodeSystem/AccessControl` in `meta.security`. No historical version of such a resource can be deleted. Blaze returns a `409 Conflict` with an `OperationOutcome` instead. In a transaction, such an entry fails the whole transaction.

## Handling Errors

| Status Code       | Description                                                                                                                     |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request` | The id or the version id given in the URL is invalid. Both are restricted to 64 characters of `A-Z`, `a-z`, `0-9`, `-` and `.`. |
| `409 Conflict`    | The given version is the current version of the resource, which can't be deleted by this interaction.                           |
| `409 Conflict`    | The resource is an [internal resource](#internal-resources), whose historical versions can't be deleted.                        |

All error responses contain an `OperationOutcome` with details in the body.
