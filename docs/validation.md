# Validation

Blaze supports validation based on profiles (`StructureDefinition`) upon resource ingest (create and update).
To enable validation, set the environment variable `ENABLE_VALIDATION_ON_INGEST` to true.

The profile, which a resource is validated against, has to exist on the server and needs to be named in `meta.profile` of the resource.
Validation also works if they are inserted as part of a [transaction/batch](./api/interaction/transaction.md) request.

Example:

```json
{
    "resourceType": "Patient",
    ...
    "meta": {
        "profile": "http://example.org/url-114730"
    }
}
```

**Note:** Validation skips empty resources in a `Bundle`, as they are not a valid use case.

## Caching

Validation profiles are cached upon initialization of the `validator`. If a new profiles is created or an existing profile is modified or deleted this cache gets invalidated and rebuilt automatically.

## Limitations

Validation is performed without terminology support.

## Performance

Validation of resources comes at a performance cost when creating or updating resources.
