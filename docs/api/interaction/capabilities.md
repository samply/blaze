# Capabilities

The [capabilities interaction][1] retrieves the capability statement describing the functionality of Blaze.

```
GET [base]/metadata
```

The response is a `CapabilityStatement` resource that describes all supported interactions, operations, search parameters, includes and revincludes per resource type. Everything stated there can be considered to be implemented correctly. If you find any discrepancies, please open an [issue][2].

## Filtering

Blaze supports filtering the capability statement by the `_elements` query parameter. Only the given top-level elements will be returned:

```
GET [base]/metadata?_elements=status,software
```

## Caching

The response contains a weak `ETag` header. Clients can use it in an `If-None-Match` header of subsequent requests, in which case Blaze returns a `304 Not Modified` without a body if the capability statement hasn't changed:

```
GET [base]/metadata
If-None-Match: W/"1e64811c"

HTTP/1.1 304 Not Modified
```

## Supported Profiles

For each resource type, `CapabilityStatement.rest.resource.supportedProfile` lists the profiles supported by Blaze. That list is derived from the `StructureDefinition` resources with `derivation` of `constraint` stored in Blaze, so it will reflect the profiles of all loaded implementation guides.

## Terminology Capabilities

If the [terminology service](../../terminology-service.md) is enabled, the mode `terminology` can be used to retrieve a `TerminologyCapabilities` resource instead. It lists all code systems available for terminology operations, including their versions:

```
GET [base]/metadata?mode=terminology
```

[1]: <https://hl7.org/fhir/http.html#capabilities>
[2]: <https://github.com/samply/blaze/issues>
