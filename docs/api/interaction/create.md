# Create

The create interaction creates a new resource with a server-assigned id. If the client wishes to have control over the id of a newly submitted resource, it should use the [update](update.md) interaction instead.

```
POST [base]/[type]
```

> [!NOTE]
> Blaze runs all writes as transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction blocks all smaller ones — including this create — until it completes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

The request body has to be a resource of the same type as the `[type]` in the URL. If the types don't match or the body is missing, Blaze returns a `400 Bad Request` with an `OperationOutcome`. Any `id`, `meta.versionId` and `meta.lastUpdated` values in the request body are ignored. All other elements of `meta` like `meta.source` are preserved.

On success, Blaze returns a `201 Created` with an `ETag` header containing the version id of the resource, a `Last-Modified` header and a `Location` header with the version-specific URL of the newly created resource:

```
HTTP/1.1 201 Created
Location: [base]/[type]/[id]/_history/[vid]
Last-Modified: Tue, 24 Jun 2025 09:03:22 GMT
ETag: W/"23"
```

By default, the response body contains the created resource. This can be controlled with the `Prefer` header:

| Value                        | Description                                                   |
|------------------------------|---------------------------------------------------------------|
| `return=representation`      | Return the created resource (default).                        |
| `return=minimal`             | Return no body.                                               |
| `return=OperationOutcome`    | Return an `OperationOutcome` instead of the resource.         |

## Referential Integrity

By default, Blaze enforces referential integrity, so all resources referenced by the resource to create have to exist already. Otherwise, Blaze returns a `409 Conflict` with an `OperationOutcome`. That behaviour can be changed by setting the [environment variable](../../deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

## Conditional Create

Blaze supports the [conditional create][1] via an `If-None-Exist` header containing the search parameters that should be used to check for existing resources:

```
If-None-Exist: identifier=095156
```

The outcome depends on the number of resources matching the search:

| Matches | Description                                                                                         |
|---------|-----------------------------------------------------------------------------------------------------|
| none    | The resource is created and a `201 Created` is returned.                                            |
| one     | No new resource is created. The already existing resource is returned with a `200 OK`.              |
| many    | Blaze returns a `412 Precondition Failed` with an `OperationOutcome` and doesn't create a resource. |

The search and the potential creation are executed in the same transaction, so conditional creates are safe against concurrent requests. An empty `If-None-Exist` header or one containing only ignorable search parameters like `_sort` results in a normal, unconditional create.

Conditional create can also be used in [transaction](transaction.md) and [batch](batch.md) requests via `Bundle.entry.request.ifNoneExist`. However references to already existing resources, currently can't be resolved. If you need this feature, please vote on the issue [Implement Conditional References](https://github.com/samply/blaze/issues/433).

[1]: <https://hl7.org/fhir/http.html#ccreate>
