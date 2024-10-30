# Admin API

> [!NOTE]
> To enable the Admin API, set the environment variable `ENABLE_ADMIN_API` to true. By default, this API is disabled for security.

> [!CAUTION]
> The Admin API exposes sensitive system information about your Blaze environment. Always enable [authentication](../authentication.md) when using the Admin API to prevent unauthorized access.

## OpenAPI Spec

```sh
curl http://localhost:8080/fhir/__admin/openapi.json
```
