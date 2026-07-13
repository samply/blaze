# Versioned Read

The versioned read interaction accesses a specific version of a resource. The available versions of a resource can be discovered via the [history](history-instance.md) interaction.

```
GET [base]/[type]/[id]/_history/[vid]
```

On success, Blaze returns the requested version of the resource with a `200 OK`. As with the [read](read.md) interaction, the response contains an `ETag` header with the version id and a `Last-Modified` header with the transaction time of that version:

```
HTTP/1.1 200 OK
Last-Modified: Tue, 24 Jun 2025 09:03:22 GMT
ETag: W/"23"
```

## Handling Errors

| Status Code       | Description                                                                                                                        |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request` | The id given in the URL is invalid. Ids are restricted to 64 characters of `A-Z`, `a-z`, `0-9`, `-` and `.`.                       |
| `404 Not Found`   | The given version of the resource doesn't exist. This includes version ids that aren't non-negative integers.                      |
| `410 Gone`        | The given version marks the deletion of the resource. The `ETag` and `Last-Modified` headers refer to the deletion.                |

All error responses contain an `OperationOutcome` with details in the body. `404 Not Found` responses carry a `Cache-Control: no-cache` header because a resource version with the given id may come into existence later.

## Deleted Histories

Versions removed via the [delete history](delete-history.md) interaction are no longer accessible and will result in a `404 Not Found`. The current version of the resource stays accessible.
