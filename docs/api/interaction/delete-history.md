# Delete History <Badge type="info" text="Feature: INTERACTION_DELETE_HISTORY"/> <Badge type="warning" text="Since 0.30.1"/>

> [!CAUTION]
> The delete history interaction is currently **alpha** and has to be enabled explicitly by setting the env var `ENABLE_INTERACTION_DELETE_HISTORY` to true. Please don't use it in production. Please be aware that the database might not be able to migrate to a newer version of Blaze if the operation was used.

> [!CAUTION]
> The delete history interaction is trial use in the unreleased next version of FHIR. So it is subject to change. Please use it with care.

The delete history interaction removes all versions of the resource except the current version.

```
DELETE [base]/[type]/[id]/_history
```

Deleting the history of a resource means that the historical versions of that resource can no longer be accessed. Subsequent [versioned reads](read.md#versioned-read) of that historical versions will return a `404 Not Found`. Versions are also removed from the [type history](history-type.md) and [system history](history-system.md). Only active [paging sessions](../../api.md#paging-sessions) and [asynchronous requests](../../api.md#asynchronous-requests) started before the delete history interaction will be able to access the deleted versions for a limited amount of time.

> [!NOTE]
> Due to stability concerns, there is a fix limit of 100,000 versions that can be deleted by this interaction. In case more than 100,000 versions exist, an OperationOutcome with code `too-costly` is returned. Currently there is no way to delete a history with more than 100,000 versions. Please open an [issue][1] if you need to delete larger histories.

[1]: <https://github.com/samply/blaze/issues>
