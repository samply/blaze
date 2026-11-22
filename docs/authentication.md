# Authentication

Blaze comes with a build-in feature to authenticate requests against an [OpenID Connect][1] provider. In order to activate this feature, the environment variable `OPENID_PROVIDER_URL` has to be set to the base URL of your OpenID Connect provider.

If this feature is activated, all FHIR Endpoints and the [Admin API](api/admin.md) will require a valid [JWT][2] access token in the [Authorization][3] header as `Bearer` token. The tokens are validated using the public keys of the JWKS referenced by the OpenID Connect configuration fetched from `<OPENID_PROVIDER_URL>/.well-known/openid-configuration`. Only RSA 256 (`RS256`) signed tokens are supported.

Two further environment variables are **mandatory** unless the [legacy mode](#legacy-mode) is enabled:

* `OPENID_PROVIDER_ISSUER` — the expected value of the `iss` (issuer) claim. Tokens with a different issuer are rejected.
* `OPENID_AUDIENCE` — the expected value of the `aud` (audience) claim. Tokens that do not include this audience are rejected.

If one of them is missing, Blaze doesn't start and logs which variable is missing.

## Modes <Badge type="warning" text="Since 1.15.0"/>

### RFC Mode

By default, Blaze validates access tokens as [RFC 9068][6] (JWT Profile for OAuth 2.0 Access Tokens), [RFC 8725][7] (JWT Best Current Practices) and [RFC 7515][8] (JWS) require from a resource server, with one deviation: the `typ` header isn't checked (see below).

### Legacy Mode

Setting `OPENID_LEGACY_MODE` to `true` keeps the validation rules of Blaze versions before 1.15.0 for deployments that can't switch at once. In legacy mode, `OPENID_PROVIDER_ISSUER` and `OPENID_AUDIENCE` are optional. If one of them isn't set, the corresponding claim isn't validated and a warning is logged at startup. The legacy mode is **deprecated** and planned for removal in the next major version. A warning is logged at startup if it is enabled.

## Validation Rules <Badge type="warning" text="Since 1.15.0"/>

A token is rejected if one of the following rules fails. Rules marked with **RFC** apply only in RFC mode.

* The token has at most 8192 characters.
* The token consists of exactly three non-empty base64url segments without padding. Encrypted tokens (JWE) and unsecured tokens (`alg` `none`) are rejected.
* The header is a JSON object.
* **RFC:** The `alg` header is exactly `RS256`. In both modes, the signature is always verified with RS256, so the header never selects the algorithm.
* **RFC:** The token has no `crit` header, since Blaze understands no extensions.
* The signature verifies with one of the public keys of the JWKS. Keys with the `kid` of the token are tried first, then all others. The headers `jwk`, `jku`, `x5u` and `x5c` are ignored, so keys only ever come from the JWKS.
* The payload is a JSON object.
* `exp` — **RFC:** required. The token is rejected if the current time is past `exp` plus a leeway of 60 seconds. In legacy mode, `exp` is checked only if present and not `null`, without leeway.
* `nbf` — if present, the token is rejected if the current time is before `nbf` minus the leeway (60 seconds in RFC mode, none in legacy mode). In legacy mode, a `null` `nbf` counts as absent.
* `iat` — **RFC:** if present, it has to be a number.

`exp`, `nbf` and `iat` are NumericDates: integers or finite decimal numbers of seconds since the epoch. Infinite values, like `1e999`, are rejected.
* `iss` — has to be equal to `OPENID_PROVIDER_ISSUER`. In legacy mode only checked if configured.
* `aud` — has to be `OPENID_AUDIENCE` or an array of strings containing it. In legacy mode only checked if configured.

The `typ` header isn't checked, in either mode, so both `JWT` and `at+jwt` are accepted. Keycloak issues `typ: JWT` unless "Use 'at+jwt' as access token header type" is enabled for the client (available since Keycloak 26.2), so every Keycloak version supported works in RFC mode.

### Public Keys

A key of the JWKS is used only if it is an RSA key (`kty` `RSA`), its `use` is `sig` or absent, its `alg` is `RS256` or absent, its `key_ops`, if present, contain `verify`, its modulus has between 2048 and 8192 bits, its public exponent is odd and between 2<sup>16</sup> and 2<sup>256</sup>, and it contains no private key members. Other keys are skipped and named by their `kid` in the log: at warning level if the skipped keys differ from the previous fetch, otherwise at debug level, so that an unchanged JWKS, like the encryption key Keycloak always publishes, doesn't log a warning every minute. At most 16 keys are used.

Keycloak generates RSA keys of 2048 bits by default. It is recommended to configure 3072 or 4096 bits in Keycloak, as [BSI TR-02102-1][9] recommends at least 3000 bits.

### JSON Documents

The token header and payload, the OpenID configuration and the JWKS are parsed with the following limits. A token exceeding them is rejected. A JWKS or OpenID configuration exceeding them fails to load, and Blaze keeps the keys fetched before.

| Limit | Value |
|---|---|
| Document size | 1 MiB |
| Nesting depth of objects and arrays | 8 |
| Digits of a number | 32 |
| Characters of a member name | 256 |
| Characters of a string | 16384 |

Duplicate member names and content after the JSON value are rejected. A custom claim mapper in Keycloak emitting JSON deeper than 8 levels makes every token of that client fail.

## Unauthorized Responses <Badge type="warning" text="Since 1.15.0"/>

A request without a valid token gets a `401 Unauthorized` response with a `WWW-Authenticate` header as [RFC 6750][10] requires. The realm is `OPENID_PROVIDER_ISSUER`, or `Blaze` in legacy mode without issuer. A request without a bearer token gets:

```text
WWW-Authenticate: Bearer realm="https://keycloak.example.com/realms/blaze"
```

A request with a rejected token gets:

```text
WWW-Authenticate: Bearer realm="https://keycloak.example.com/realms/blaze", error="invalid_token"
```

The reason a token was rejected is logged at debug level only.

The FHIR API answers with an `OperationOutcome` with the code `MSG_AUTH_REQUIRED`; the Admin API with the JSON body `{"msg": "Authentication required."}`.

As long as Blaze has no usable public key, a request with a bearer token can't be checked. It gets a `503 Service Unavailable` response without `WWW-Authenticate` header and can be retried later. Requests without a bearer token still get a 401. Blaze has no usable public key before the first successful fetch of the JWKS, one second after the start or later if the OpenID provider isn't reachable, and after it fetched a JWK Set without usable key, which Blaze also logs as error on every fetch.

## Usage

In order to test the authentication feature, please start first [Keycloak][4] and then Blaze as defined in the Docker Compose file in the `docs/authentication` directory:

```sh
docker compose up keycloak
```

wait until keycloak is started

```sh
docker compose up blaze
```

after both services are up, please run:

```sh
ACCESS_TOKEN=$(./fetch-token.sh) ./request-all-resources.sh
```

The output should be:

```json
{
  "resourceType": "Bundle",
  "id": "C6IJYWHRYMGMXUFH",
  "type": "searchset",
  "total": 0,
  "link": [
    {
      "relation": "self",
      "url": "http://localhost:8080/fhir?_count=50"
    }
  ]
}
```

## Additional Considerations

* Blaze fetches the public keys from the JWKS referenced under `<OPENID_PROVIDER_URL>/.well-known/openid-configuration` one second after the start and every minute afterwards. Only a valid JWK Set, a JSON object with a `keys` array of JSON objects, replaces the keys fetched before. If a fetch doesn't return one (network error, error status, invalid or too large document, or a JSON document that isn't a JWK Set, like `{}` or an error object), the keys fetched before are kept. A JWK Set without usable key replaces them like any other JWK Set, so keys removed by the provider are never used again.
* Only RSA 256 signatures are supported (please file an [issue][5] if you need other algorithms).

[1]: <https://openid.net/connect/>
[2]: <https://tools.ietf.org/html/rfc7519>
[3]: <https://tools.ietf.org/html/rfc7235#section-4.2>
[4]: <https://www.keycloak.org>
[5]: <https://github.com/samply/blaze/issues>
[6]: <https://www.rfc-editor.org/rfc/rfc9068>
[7]: <https://www.rfc-editor.org/rfc/rfc8725>
[8]: <https://www.rfc-editor.org/rfc/rfc7515>
[9]: <https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Publications/TechGuidelines/TG02102/BSI-TR-02102-1.html>
[10]: <https://www.rfc-editor.org/rfc/rfc6750#section-3>
