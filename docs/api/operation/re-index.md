# Operation \$re-index <Badge type="info" text="Feature: OPERATION_RE_INDEX"/> <Badge type="warning" text="Since 1.14.0"/>

The system level \$re-index operation (re)indexes all resources of one search parameter. It is useful after adding a custom search parameter via `DB_SEARCH_PARAM_BUNDLE`, because Blaze indexes only newly written resources using such a search parameter.

```
POST [base]/$re-index
```

The operation has to be enabled explicitly by setting the env var [`ENABLE_OPERATION_RE_INDEX`](../../deployment/environment-variables.md#enable-operation-re-index) to `true`.

The operation is formally described by the OperationDefinition with the canonical URL `https://blaze-server.org/fhir/OperationDefinition/re-index`, defined in the Blaze IG.

## In Parameters

| Name             | Cardinality | Type      | Documentation                                                                         |
|------------------|-------------|-----------|---------------------------------------------------------------------------------------|
| search-param-url | 1..1        | canonical | The URL of the search parameter to (re)index without version. It has to be known to the server. |

A request without the `search-param-url` parameter, with the URL of a search parameter unknown to the server or with a versioned canonical like `http://hl7.org/fhir/SearchParameter/clinical-code|4.0.1` is rejected with a `400 Bad Request` response. Versions are not supported, because Blaze knows only one search parameter per URL.

### Response

The response will be always async according to the [Asynchronous Interaction Request Pattern][1] from FHIR R5. Polling the async status endpoint of a finished re-index returns a batch-response Bundle with a single entry with status `200`. The progress, like the total number of resources and the number of resources already processed, is available as outputs of the underlying ReIndexJob Task resource, which can also be inspected in the [Frontend](../../frontend.md).

### Example

```sh
curl -s -X POST -H 'Content-Type: application/fhir+json' \
  -d '{"resourceType": "Parameters", "parameter": [{"name": "search-param-url", "valueCanonical": "http://hl7.org/fhir/SearchParameter/clinical-code"}]}' \
  -D - -o /dev/null 'http://localhost:8080/fhir/$re-index'
```

The `Content-Location` header of the `202 Accepted` response contains the URL of the async status endpoint.

[1]: <http://hl7.org/fhir/R5/async-bundle.html>
