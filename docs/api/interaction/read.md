# Read

The read interaction accesses the current version of a resource.

```
GET [base]/[type]/[id]
```

On success, Blaze returns the resource with a `200 OK`. The response contains an `ETag` header with the version id of the resource and a `Last-Modified` header with the transaction time of its last change:

```
HTTP/1.1 200 OK
Last-Modified: Tue, 24 Jun 2025 09:03:22 GMT
ETag: W/"23"
```

The version id in the `ETag` header is identical to `meta.versionId` of the returned resource and can be used in an `If-Match` header of a subsequent [update](update.md) to detect concurrent modifications.

## Handling Errors

| Status Code       | Description                                                                                                                                                                                                               |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request` | The id given in the URL is invalid. Ids are restricted to 64 characters of `A-Z`, `a-z`, `0-9`, `-` and `.`.                                                                                                              |
| `404 Not Found`   | No resource with the given id exists.                                                                                                                                                                                     |
| `410 Gone`        | The resource existed but is deleted. The `ETag` and `Last-Modified` headers refer to the deletion. Past versions are still accessible via the [versioned read](vread.md) and [history](history-instance.md) interactions. |

All error responses contain an `OperationOutcome` with details in the body.

## Conditional Read

Conditional reads via the `If-Modified-Since` or `If-None-Match` headers are not implemented yet. Accordingly Blaze advertises `CapabilityStatement.rest.resource.conditionalRead` as `not-supported`.
