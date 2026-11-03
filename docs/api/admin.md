# Admin API

> [!NOTE]
> To enable the Admin API, set the environment variable `ENABLE_ADMIN_API` to true. By default, this API is disabled for security.

> [!CAUTION]
> The Admin API exposes sensitive system information about your Blaze environment. Always enable [authentication](../authentication.md) when using the Admin API to prevent unauthorized access.

## Authentication

If [authentication](../authentication.md) is enabled, all endpoints of the Admin API require a valid token.

### Unauthorized Response

Requests without a valid token are answered with `401 Unauthorized`. The body of that response matches the format of the endpoint:

* JSON endpoints, like `/fhir/__admin/dbs`, return a small JSON object:

```json
{"msg": "Authentication required."}
```

* FHIR endpoints, like `/fhir/__admin/Task`, return an OperationOutcome with the issue code `login`.

## OpenAPI Spec

```sh
curl http://localhost:8080/fhir/__admin/openapi.json
```
