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

#### WWW-Authenticate <Badge type="warning" text="Since 1.15.0"/>

Every `401 Unauthorized` response has a `WWW-Authenticate` header as [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750#section-3) requires. Its realm is `OPENID_PROVIDER_ISSUER`. Requests without a bearer token get no error code, requests with a rejected token get `invalid_token`:

```text
WWW-Authenticate: Bearer realm="https://keycloak.example.com/realms/blaze"
WWW-Authenticate: Bearer realm="https://keycloak.example.com/realms/blaze", error="invalid_token"
```

See [Authentication](../authentication.md#unauthorized-responses) for details.

#### Service Unavailable <Badge type="warning" text="Since 1.15.0"/>

As long as Blaze has no usable public key of the OpenID provider, for example right after the start or after the provider published a JWK Set without usable key, a request with a bearer token can't be checked and is answered with `503 Service Unavailable` without `WWW-Authenticate` header. JSON endpoints return the reason as `msg`, FHIR endpoints an OperationOutcome with the issue code `transient`.
