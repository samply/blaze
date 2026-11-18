# Delete History <Badge type="info" text="Feature: INTERACTION_DELETE_HISTORY"/> <Badge type="warning" text="Since 0.30.1"/> <Badge type="tip" text="Stable 1.14.0"/>

> [!NOTE]
> The delete history interaction is an optional feature that has to be enabled by setting [`ENABLE_INTERACTION_DELETE_HISTORY`](../../deployment/environment-variables.md#enable-interaction-delete-history) to `true`. Before 1.14.0, it was **alpha** and shouldn't be used in production.

The delete history interaction removes all versions of the resource except the current version.

```
DELETE [base]/[type]/[id]/_history
```

> [!NOTE]
> Blaze runs all writes as transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction blocks all smaller ones — including this delete history — until it completes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

Deleting the history of a resource means that the historical versions of that resource can no longer be accessed. Subsequent [versioned reads](vread.md) of that historical versions will return a `404 Not Found`. Versions are also removed from the [type history](history-type.md) and [system history](history-system.md). Only active [paging sessions](../../api.md#paging-sessions) and [asynchronous requests](../../api.md#asynchronous-requests) started before the delete history interaction will be able to access the deleted versions for a limited amount of time.

> [!NOTE]
> Due to stability concerns, there is a fix limit of 100,000 versions that can be deleted by this interaction. In case more than 100,000 versions exist, an OperationOutcome with code `too-costly` is returned. Currently there is no way to delete a history with more than 100,000 versions. Please open an [issue][1] if you need to delete larger histories.

A single historical version can be deleted via the [delete history version](delete-history-version.md) interaction.

## Internal Resources <Badge type="warning" text="Since 1.14.0"/>

Resources Blaze owns itself can carry the security label `internal` with the system [`https://blaze-server.org/fhir/CodeSystem/AccessControl`](/fhir/CodeSystem-AccessControl.html){target="_self"} in `meta.security`. The history of such a resource can't be deleted, neither at once nor version by version via the [delete history version](delete-history-version.md) interaction. Blaze returns a `409 Conflict` with an `OperationOutcome` instead.

[1]: <https://github.com/samply/blaze/issues>
