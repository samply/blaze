# Update

The update interaction creates a new current version for an existing resource or creates an initial version if no resource already exists for the given id.

```
PUT [base]/[type]/[id]
```

The request body has to be a resource with an `id` element that matches the `[id]` in the URL. If the `id` is missing or doesn't match, Blaze returns a `400 Bad Request` with an `OperationOutcome`. Any `meta.versionId` and `meta.lastUpdated` values in the request body are ignored.

On success, Blaze returns either a `200 OK` if an already existing resource was updated or a `201 Created` if a new resource was created. In both cases the response contains an `ETag` header with the version id of the resource and a `Last-Modified` header. On creation, a `Location` header with the version-specific URL of the resource is returned as well.

By default, the response body contains the updated resource. This can be controlled with the `Prefer` header:

| Value                        | Description                                                   |
|------------------------------|---------------------------------------------------------------|
| `return=representation`      | Return the updated resource (default).                        |
| `return=minimal`             | Return no body.                                               |
| `return=OperationOutcome`    | Return an `OperationOutcome` instead of the resource.         |

## Update as Create

Blaze allows a client to create a resource with a client-assigned id by updating a resource that doesn't exist yet. In this case an initial version of the resource is created and a `201 Created` is returned. Blaze advertises this behaviour with `CapabilityStatement.rest.resource.updateCreate` set to `true`.

## Identical Content

Blaze keeps track over the history of all updates of each resource. However if the content of the resource update is equal to the current version of the resource, no new history entry is created. Usually such identical content updates will only cost a very small amount of transaction handling storage but no additional resource or index storage.

## Managing Resource Contention

In order to prevent lost updates, a client can perform a version-aware update by sending an `If-Match` header with the version id of the resource it expects to update:

```
If-Match: W/"23"
```

Blaze only performs the update if the current version id of the resource matches one of the version ids in the `If-Match` header. Otherwise it returns a `412 Precondition Failed`. A comma-separated list of version ids is supported, in which case the update succeeds if any of the version ids matches the current one.

## Read-Only Resources

Some resources like base FHIR StructureDefinition resources are read-only and can't be updated. That resources contain two tags with code `read-only` — one with the system `https://blaze-server.org/fhir/CodeSystem/AccessControl` and one with the legacy system `https://samply.github.io/blaze/fhir/CodeSystem/AccessControl`. Blaze emits both so that downgrading to an older Blaze version, which enforces read-only via the legacy tag, keeps these resources protected; the legacy tag will be removed in the next major version (v2).

## Conditional Update

Blaze supports the wildcard variant of the [conditional update][1] using an `If-None-Match: *` header. With this header, the update is only performed if no version of the resource exists yet. If a resource with the given id already exists, Blaze returns a `412 Precondition Failed` instead of overwriting it:

```
If-None-Match: *
```

This is useful to safely create a resource with a client-assigned id without risking to overwrite an already existing one.

The general, search-based conditional update via `PUT [base]/[type]?[search parameters]` is not implemented yet. Accordingly Blaze advertises `CapabilityStatement.rest.resource.conditionalUpdate` as `false`. Please see issue [#361](https://github.com/samply/blaze/issues/361) for more information.

[1]: <https://hl7.org/fhir/http.html#cond-update>
