# JWT Verification Without buddy

## Intent

Blaze authenticates FHIR and admin requests by verifying an OpenID Connect bearer token (a JWT signed with RS256) against the public keys of the provider's JWKS. Today this uses three buddy libraries, `buddy-auth`, `buddy-sign` and `buddy-core`, which pull in BouncyCastle (`bcprov`, `bcpkix`, `bcutil`, about 9.5 MB), `cheshire` (with `tigris` and the Jackson Smile data format), `clout` and `instaparse`.

Blaze uses none of BouncyCastle's algorithms: RS256 is `SHA256withRSA`, which the JDK has. Still, BouncyCastle had to be pinned to 1.87 to fix six CVEs, and every one of these libraries has to be tracked for CVEs. The bearer token is untrusted input on every request, so the code parsing it is the most exposed piece of Blaze's authentication.

This design replaces buddy with a small amount of Blaze-owned code on top of the JDK and jsonista, which Blaze already uses. The aim is security: a smaller attack surface and fewer libraries to track. While replacing the verification anyway, Blaze also becomes conformant to the specifications for resource servers (RFC 9068, RFC 8725, RFC 7515) by default, and keeps today's validation rules as an opt-in legacy mode.

## Goals

* Remove `buddy-auth`, `buddy-sign`, `buddy-core`, BouncyCastle, `clout`, `instaparse`, `cheshire`, `tigris` and the `jackson-dataformat-smile` pin from the server classpath.
* Verify RS256 tokens using only the JDK (`KeyFactory`, `Signature`, `Base64`) and jsonista.
* Validate access tokens as RFC 9068 requires from a resource server, by default, with one documented deviation: `typ` isn't checked (D5).
* Offer a legacy mode, enabled by `OPENID_LEGACY_MODE=true`, which accepts the tokens accepted today, for deployments that can't switch at once.
* Write down every validation rule explicitly, so that it is reviewed and tested, instead of inheriting a library's defaults.
* Parse as little unauthenticated input as possible: decode the payload only after its signature has been verified.
* Report invalid tokens as anomalies, not exceptions, and never log a token.

## Non-Goals

* No algorithms other than RS256 (no RS384/512, PS*, ES*, EdDSA). They can be added later as separate issues.
* No JWE (encrypted tokens), no token introspection, no opaque tokens.
* No authorization (scopes, roles). `:identity` stays a plain map of claims that nothing reads beyond "is it there".
* No early JWKS refetch on an unknown `kid`. Keys are fetched every 60 seconds as today, and Keycloak publishes new keys ahead of using them.
* No copy of buddy's accidental behaviour (see Legacy Mode). Legacy mode keeps today's *rules*, not today's quirks.
* Keycloak is the provider Blaze is designed and tested against. Other providers work as far as they follow the same specifications.

## Specifications

Blaze is a resource server: it receives access tokens. OpenID Connect Core covers ID tokens validated by the client and doesn't govern access tokens. The relevant requirements:

* **RFC 9068 §4** (JWT access tokens), for resource servers: `typ` MUST be `at+jwt` or `application/at+jwt`; `iss` MUST exactly match the issuer; `aud` MUST contain the resource server's identifier; the signature MUST be verified with the algorithm of the `alg` header, `none` MUST be rejected, only the authorization server's keys MUST be used; the current time MUST be before `exp`, with "some small leeway, usually no more than a few minutes"; failures MUST be answered per RFC 6750 §3.1 with the error code `invalid_token`.
* **RFC 8725 §3.1** (JWT BCP): the verifier "MUST ensure that the `alg` … header specifies the same algorithm that is used for the cryptographic operation". **§3.10:** `kid` must not be trusted blindly; `jku`/`x5u` must not be followed. **§3.11:** explicit typing distinguishes kinds of JWTs, e.g. access tokens from ID tokens.
* **RFC 7515 §4.1.4:** `kid` is "a hint" and OPTIONAL, so trying all keys is conformant. **§4.1.11:** if an extension listed in `crit` isn't understood by the recipient, "the JWS is invalid".
* **OpenID Connect Core §10.1:** the signer MUST set `kid` if the JWKS has several keys; the verifier re-fetches the JWKS when it sees an unfamiliar `kid`.

**Keycloak and `typ`:** since Keycloak 26.2 ([keycloak#37573](https://github.com/keycloak/keycloak/pull/37573)), each client has the setting "Use 'at+jwt' as access token header type" (Clients → client → Advanced → Fine grain OpenID Connect configuration; attribute `access.token.header.type.rfc9068`). It is off by default, so Keycloak issues `typ: JWT` unless it's switched on for each client requesting tokens for Blaze. Older versions, such as Keycloak 24, always issue `typ: JWT`. This is why Blaze doesn't check `typ` (D5).

## Current Behaviour

Read from the source of the versions on `main` (`buddy-sign` 3.4.333, `buddy-core` 1.12.0-430, `buddy-auth` 3.0.323):

| Aspect | Current behaviour |
|---|---|
| Key loading | Every JWK with `"use": "sig"` is converted by `buddy.core.keys/jwk->public-key`, dispatching on `kty`, with the `BC` provider. A JWK without `use` is dropped. A `sig` key of another `kty` (e.g. EC) is kept and fails on every request with an error log. An unknown `kty` throws inside the scheduled task. No minimum key size. |
| Key selection | `kid` is ignored. Every key is tried in turn until one verifies. |
| Algorithm | The verifier is fixed to RS256 by the `{:alg :rs256}` option. The header `alg` is parsed but **not compared**. A missing or non-string `alg` makes parsing fail, so the token is rejected. |
| Other headers | `crit`, `jwk`, `jku`, `x5u`, `x5c`, `typ` are ignored. |
| `exp` | Checked only if present: rejected if `exp <= now`. **A token without `exp` is accepted.** |
| `nbf` | Checked only if present: rejected if `nbf > now`. |
| `iat` | Not checked. |
| Leeway | 0 seconds. |
| `iss` | If configured, must equal the claim exactly. Not configured: not checked, warning at startup. |
| `aud` | If configured, must equal the claim if it is a string, or be contained in it if it is an array. A token without `aud` is rejected. Not configured: not checked, warning at startup. |
| Malformed tokens | Exceptions, caught in `unsign`. `:validation` errors are logged at trace, all others at error with the exception message. The token is not logged. |
| Size limits | None in buddy. Jetty limits the request header block to 8 KiB, which bounds the token. |
| JSON | Cheshire, duplicate keys allowed (last wins), claims with keyword keys. The header is parsed before the signature is verified. |
| Result | The claims map is assoc'ed as `:identity` on the request. The auth guards only check `(boolean (:identity request))`. |
| 401 response | From Blaze's auth guards: an `OperationOutcome` with `MSG_AUTH_REQUIRED` for FHIR, `{"msg": "Authentication required."}` for the admin API. No `WWW-Authenticate` header. |

## Approaches

### 1. JDK-only RS256 verification in Blaze (chosen)

* Convert each JWK into an `RSAPublicKey` with `RSAPublicKeySpec` from the base64url `n` and `e`, through `KeyFactory.getInstance("RSA")` (JDK provider).
* Verify with `Signature.getInstance("SHA256withRSA")` over the ASCII bytes of `header.payload`.
* Parse header and payload with jsonista.
* Replace `IAuthentication` and `wrap-authentication` with a Blaze-owned protocol and middleware of a few lines.

Pros: no new library; the only code parsing untrusted input is Jackson (already on the classpath for FHIR parsing) and the JDK. Every rule is visible in Blaze's code and tests, so both modes are easy to express.

Cons: Blaze owns security-relevant code, about 150 lines. This is mitigated by supporting a single algorithm, by keeping every rule explicit, and by a test suite of attack vectors (see Test Plan).

### 2. Nimbus JOSE+JWT

The latest version is 10.10 (about 840 KB). All its dependencies are optional (BouncyCastle, Tink); RSA works with the JDK alone, and it shades its own Gson.

Pros: widely used and reviewed; complete JOSE coverage; has an RFC 9068 `typ` verifier.

Cons: a new library to track, which is contrary to the goal. It has a CVE history of its own, mostly DoS: CVE-2023-52428 (PBES2 iteration count), CVE-2025-53864 (deeply nested JSON in the shaded Gson), and older ones (CVE-2017-12972/12973/12974, CVE-2019-17195). Most of these are in code Blaze would never use (JWE, ECDH), but it would be on the classpath and in the scanner reports. Blaze already excludes `nimbus-jose-jwt` from the HAPI dependencies in `admin-api` and `terminology-service-local`, so adding it would undo that. Legacy mode would have to be built from its configuration options.

### 3. Google Tink JWT

Tink 1.24.0 is already on the classpath through `page-id-cipher`, so it adds no new library. Checked against the 1.24.0 source:

* `JwkSetConverter.toPublicKeysetHandle` **requires `alg` on every JWK** and throws for the whole set if one is missing or not one of `RS*`, `PS*`, `ES*`. Keycloak's JWKS has an `RSA-OAEP` encryption key, so the whole set fails unless Blaze filters keys first. The Microsoft Entra ID JWKS has no `alg` at all (checked 2026-11-22).
* A `use` other than `sig` throws instead of being skipped; `key_ops` must be exactly `["verify"]`.
* The `JwtValidator` can expect a `typ`, requires `exp` unless `allowMissingExpiration()`, limits the leeway to 10 minutes, rejects `crit` and requires keys of at least 2048 bits. It doesn't allow ignoring the header `alg`.
* The API reports failures as `GeneralSecurityException`, which needs the same catch-at-the-boundary as the JDK.

Pros: no new library; good defaults, close to the RFC mode.

Cons: Blaze would have to filter and rewrite the JWKS JSON before handing it to Tink, so Blaze handles JWKs anyway, plus Tink's Gson-based parsing of the token. Legacy mode can't be expressed in full. Tink's JWT support is far larger than RS256 verification.

### Choice

Approach 1. It removes the most code from the attack surface, adds no library, and expresses both modes as a few explicit rules.

## Design

### Modules and Namespaces

* **New module `modules/auth`** (`blaze/auth`, depends only on `module-base`), added to the CI module matrix (D6).
  * `blaze.auth.protocols`: `(defprotocol AuthenticationBackend (-authenticate [backend request]))`. It returns the identity, or an anomaly carrying `:blaze.auth/realm` (D19). buddy's separate `-parse` step is dropped: it only exists so buddy can chain backends.
  * `blaze.auth`: `authenticated?` and `www-authenticate`, which builds the header value from a failure anomaly.
  * `blaze.middleware.authentication`: `(wrap-authentication handler backend)` with a single backend (D18). It assocs `:identity` if the backend returns an identity, and the anomaly as `:blaze.auth/failure` otherwise. The auth guards build the `WWW-Authenticate` header from that failure. It supports the arities Blaze calls.
* `rest-util`, `rest-api`, `admin-api` and `openid-auth` depend on `blaze/auth` instead of `buddy-auth`.
* The optional collection `:auth-backends` of `:blaze/rest-api` and `:blaze/admin-api` becomes an optional single `:auth-backend`, specced as `#(satisfies? p/AuthenticationBackend %)`. The auth guards and `wrap-authentication` are applied only if it is present, as today with `(seq auth-backends)`.
* The wiring moves from `src/blaze/system.clj` (`(ig/refset :blaze.auth/backend)` in the root config) into the OpenID feature in `resources/blaze.edn`, which adds `:blaze/rest-api {:auth-backend #blaze/ref :blaze.openid-auth/backend}` and the same for `:blaze/admin-api`. The feature configs and the root config are merged deeply, so this only takes effect if `OPENID_PROVIDER_URL` is set. The `:blaze.auth/backend` hierarchy and the `derive` in `blaze.openid-auth` are removed.
* `openid-auth` gets two `impl` namespaces:
  * `blaze.openid-auth.jwk`: JWKS JSON → a vector of `{:kid <string or nil> :key <RSAPublicKey>}`.
  * `blaze.openid-auth.jwt`: `(verify keys opts now token)` → the claims, or an anomaly. `opts` carries the mode, issuer and audience. Pure apart from the JDK `Signature` object, which is created per call because it isn't thread-safe.
* `openid-auth` fetches the OpenID configuration and the JWKS with hato `:as :stream` and parses the stream with the strict JSON mapper (see JSON), which stops at 1 MiB (D20), instead of `:as :json`, which needs cheshire.
* `now` comes from the `:blaze/clock` component, which the backend gets as a new Integrant dependency in `resources/blaze.edn` (D10).

### Configuration

* **`OPENID_LEGACY_MODE`**, new, default `false`. Passed to the backend as `:legacy-mode?`. Documented in `docs/deployment/environment-variables.md`.
* **RFC mode** (default): `OPENID_PROVIDER_ISSUER` and `OPENID_AUDIENCE` are **mandatory**. If either is missing, the backend fails to initialize with a message naming the missing variable and pointing to `OPENID_LEGACY_MODE`, so Blaze doesn't start. The startup warnings about missing `iss`/`aud` become obsolete in this mode.
* **Legacy mode:** `OPENID_PROVIDER_ISSUER` and `OPENID_AUDIENCE` stay optional. If one is missing, the startup warning of today is logged unchanged (`OPENID_PROVIDER_ISSUER is not set; issuer (iss) claim will not be validated.` and `OPENID_AUDIENCE is not set; audience (aud) claim will not be validated.`). Additionally a warning at startup states that legacy mode is enabled, deprecated and planned for removal in the next major version.

### The Contract Stays

* `:identity` on the request is the claims map with keyword keys, as today.
* There is at most one backend (D18). Today's support for several backends, the first identity winning, is dropped, since `openid-auth` is the only one.
* The auth guards' 401 bodies are unchanged. The guards add a `WWW-Authenticate` header (see below, D15).
* The test backend in `test/blaze/system_test.clj` becomes a `reify` of the new protocol.

### Key Loading Rules (JWKS), Both Modes

A JWK is used only if all of these hold. Otherwise it is skipped with a message naming its `kid` (never key material), logged at WARN if the skipped keys differ from the previous fetch and at DEBUG otherwise (D28):

1. `kty` is `"RSA"`.
2. `use` is `"sig"`, or absent (RFC 7517 makes it optional).
3. `alg` is `"RS256"`, or absent.
4. `key_ops`, if present, contains `"verify"`.
5. `n` and `e` are strings of at most 1366 and 43 characters, checked **before** decoding, and are strict base64url (no padding) decoding to positive integers (D20).
6. The modulus has at least 2048 bits (D14) and at most 8192 bits (D20).
7. The public exponent is odd with 2^16 < e < 2^256, the range of FIPS 186-5. This rules out the trivially forgeable `e = 1` and exponents that make verification expensive (D20).
8. No private key members (`d`, `p`, `q`, `dp`, `dq`, `qi`).

`kid` is kept if it is a string. At most 16 keys are used, in the order of the JWKS; further usable keys are skipped with a warning (D20). The fetch fails, as today, if no key remains. A fetch without a valid JWK Set (network error, error status, invalid or too large document, a document that isn't a JWK Set) keeps the previously fetched keys, as today (D31). A JWK Set without usable key replaces them with none, unlike today (D30).

The OpenID configuration and the JWKS document are parsed with the strict JSON mapper (see JSON), so each is limited to 1 MiB; a larger response fails the fetch (D20).

Compared with today, rule 2 accepts keys without `use` (Keycloak always sets it), and rules 1, 5, 6 and 7 skip keys that are either unusable for RS256 or weak. Legacy mode doesn't keep today's key handling: skipping an EC key at fetch time instead of failing on every request is no rule a token relies on.

### Token Verification Rules

In this order. The first failing rule ends verification with an anomaly. Rules marked **RFC** apply in RFC mode only; legacy mode skips them.

1. **Size:** the token is at most 8192 characters (D8). Jetty already limits the whole header block to 8 KiB, so no token that reaches Blaze today is rejected by this.
2. **Shape:** the token matches `^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`: exactly three non-empty segments of the base64url alphabet without padding. This rules out JWE (five segments), unsecured JWTs with an empty signature, padding and whitespace before any decoding happens.
3. **Header:** base64url-decode and parse with a dedicated jsonista mapper (see JSON). It must be a JSON object. Both modes need it for `kid`.
4. **RFC: `alg`** must be exactly the string `"RS256"` (RFC 8725 §3.1, RFC 9068 §4). The verifier is fixed to RS256 in both modes, so the header never selects the algorithm; in legacy mode `alg` isn't looked at.
5. **`typ` is not checked**, in either mode (D5).
6. **RFC: `crit`**: a token with a `crit` header is rejected, since Blaze understands no extensions (RFC 7515 §4.1.11).
7. `typ`, `jwk`, `jku`, `x5u`, `x5c`, `x5t`, `cty` and unknown headers are ignored in both modes: keys are only ever taken from the JWKS (D13).
8. **Key order** (D7): `kid` is used as an optimization only. Keys whose `kid` equals the header `kid` are tried first, then all others. So the result is the same as trying all keys, as today, and a valid token usually needs one signature check. A header `kid` that isn't a string is treated as absent.
9. **Signature:** base64url-decode the signature. Its length must equal the byte length of the key's modulus (checked per key). Verify `SHA256withRSA` over the ASCII bytes of the first two segments as received.
10. **Payload:** only now base64url-decode and parse the payload. It must be a JSON object.
11. **Claims** (`now` in epoch seconds, leeway `L`):

| Claim | RFC mode | Legacy mode |
|---|---|---|
| `exp` | required, a NumericDate (D32); rejected if `now >= exp + L` | if present and not `null` (D29): a NumericDate; rejected if `now >= exp` |
| `nbf` | if present: a NumericDate; rejected if `now < nbf - L` | if present and not `null` (D29): a NumericDate; rejected if `now < nbf` |
| `iat` | if present: a NumericDate | not checked |
| `iss` | a string equal to the configured issuer | if configured: as in RFC mode |
| `aud` | the configured audience as a string, or an array of strings containing it | if configured: as in RFC mode |
| `L` | 60 seconds (D9) | 0, as today |

12. Return the claims with keyword keys.

### `WWW-Authenticate` (D15)

RFC 6750 §3 requires the header on every 401 of a bearer-protected resource, and the `Bearer` scheme "MUST be followed by one or more auth-param values". Both auth guards (FHIR and admin API) add it to their 401 responses, in both modes, since it doesn't change which tokens are accepted:

* No credentials (no `Authorization` header, or another scheme). RFC 6750 §3.1: "SHOULD NOT include an error code":
  `WWW-Authenticate: Bearer realm="<realm>"`
* Credentials rejected (the failure has `:blaze.auth/error`, e.g. invalid signature, expired, wrong `iss` or `aud`):
  `WWW-Authenticate: Bearer realm="<realm>", error="invalid_token"`

No `error_description`, so a client can't probe which check failed; the reason is in the debug log. `insufficient_scope` and `invalid_request` don't apply. RFC 9728's `resource_metadata` parameter is out of scope. The realm is the configured issuer (D17).

### Base64url

A dedicated function uses `Base64.getUrlDecoder()` after the shape regex guarantees the unpadded alphabet. Non-canonical trailing bits are not rejected: the signature covers the encoded text, so an alternative encoding of header or payload doesn't verify, and an alternative encoding of the signature decodes to the same bytes.

### JSON

A new namespace `blaze.http-client.json` in the `http-client` module provides the generic part, with no limits of its own (D21): `(object-mapper constraints)` builds a jsonista `ObjectMapper`, `(parse-json mapper input-stream)` returns the parsed data or an anomaly, and `(parse-response mapper response)` parses the body stream of an HTTP response if its status is 2xx and otherwise closes the stream and returns a fault naming the status. The fetches request with `:as :stream` and `:throw-exceptions false`, so that the body stream of an error response is closed, as `:as :json` did before, instead of being left open in the thrown exception. The constraints are a Clojure map, from which `object-mapper` builds the `StreamReadConstraints`, so the callers don't use Jackson's builder themselves:

```clojure
{:max-nesting-depth 8
 :max-number-length 32
 :max-name-length 256
 :max-string-length 16384
 :max-document-length 1048576}
```

Every key is optional; a missing key keeps Jackson's default. The keys are specced as `pos-int?` in `blaze.http-client.json.spec`, and `object-mapper`, `parse-json` and `parse-response` have function specs in `blaze.http-client.json-spec`, both in `src`, since other modules call them. Nothing calls it implicitly; the `:blaze/http-client` component and its other users (`fhir-client` through `extern-terminology-service` and `extern-validator`, which parse with Blaze's FHIR parser) are unchanged. `openid-auth` defines one strict mapper for the token header and payload, the JWKS and the OpenID configuration; `openid-client` defines its own for the OpenID configuration and the token response. Both mappers have:

* keyword keys (`:identity` stays the same);
* `STRICT_DUPLICATE_DETECTION` enabled, so `{"alg":"none","alg":"RS256"}` is rejected instead of last-one-wins;
* `FAIL_ON_TRAILING_TOKENS` enabled, so the parser reads the input to its end and `maxDocumentLength` counts every byte (D26); trailing content after the JSON value is rejected, trailing whitespace is fine;
* `StreamReadConstraints` (D22):

| Constraint | Jackson default | Blaze | Reasoning |
|---|---|---|---|
| `maxNestingDepth` | 1000 | 8 | deepest real document: a payload with the UMA `authorization` claim, 5 levels (Keycloak's `resource_access.<client>.roles`: 4, JWKS: 4, header: 2) |
| `maxNumberLength` | 1000 digits | 32 digits | `exp`, `iat`, `nbf` and `expires_in` have about 10 |
| `maxNameLength` | 50,000 | 256 | the longest real names have about 40 characters |
| `maxStringLength` | 20,000,000 | 16 KiB in `openid-auth`, 64 KiB in `openid-client` | the largest strings in `openid-auth` are `x5c` certificates (≈ 2 KB); `openid-client` gets an `access_token` in its token response, which Blaze's 8192-character limit doesn't cover |
| `maxDocumentLength` | unlimited | 1 MiB | the limit of D20 and D21; a token's header and payload are far below it |

A document exceeding a constraint fails to parse: a token is rejected, a fetch fails and keeps the previous keys or token.

### Errors and Logging

* Every JDK or Jackson exception (`IllegalArgumentException` from Base64, `JsonProcessingException`, `InvalidKeyException`, `SignatureException`) is caught right at the call that throws it with `ba/try-one` or `ba/try-all` and turned into an anomaly with a **fixed** message such as `"Invalid token header."` or `"Token expired."`. No exception escapes `verify`, and no control flow uses exceptions.
* Inside `verify`: category `::anom/incorrect` for malformed tokens, `::anom/forbidden` for well-formed but rejected tokens (bad signature, expired, wrong `alg`, `iss` or `aud`). The backend logs the message and turns any of them into the rejected-credentials failure of D19, so the client always gets the same 401.
* The backend logs a rejection at debug with the fixed message only. Neither the token nor values taken from it (claims, `kid`, header values) are ever logged, which also rules out log injection.

### Dependencies Removed

From `openid-auth`: `buddy/buddy-auth`, `buddy/buddy-core`, `cheshire/cheshire`, the `jackson-dataformat-cbor` and `-smile` pins, `org.bouncycastle/bcpkix-jdk18on` and `bcprov-jdk18on` with their CVE comments. From `rest-util`: `buddy/buddy-auth`. Transitively gone: `buddy-sign`, `bcutil`, `clout`, `instaparse`, `tigris`, `jackson-dataformat-smile`. `jackson-dataformat-cbor` and `commons-codec` stay, both used elsewhere. Also: the `bouncycastle` group in `renovate.json` and the `buddy.auth` alias in `.clj-kondo/root/config.edn`.

From `openid-client`: `cheshire/cheshire` and the `jackson-dataformat-cbor` and `-smile` pins. It fetches the OpenID configuration and posts to the token endpoint with hato `:as :stream` and parses the responses with its own strict mapper (see JSON), like `openid-auth` (D11). So `cheshire` leaves the classpath completely.

## Client-Observable Differences

Status codes and response bodies stay the same, except that a request with a bearer token gets a 503 instead of a 401 as long as no public keys are available (D27). Every 401 gets a `WWW-Authenticate` header in both modes (D15). The RFC mode is a **breaking change** for existing deployments, which is why legacy mode exists:

| Change | RFC mode | Legacy mode |
|---|---|---|
| `OPENID_PROVIDER_ISSUER` or `OPENID_AUDIENCE` not set | Blaze doesn't start | unchanged (warning) |
| Header `alg` other than `RS256` with a valid RS256 signature | rejected | accepted |
| `crit` header | rejected | ignored |
| No `exp` claim | rejected | accepted |
| Expiry around `now` | 60 s leeway | exact |
| Duplicate JSON keys | rejected | rejected |
| Base64 padding in a segment | rejected | rejected |
| Key below 2048 bits | skipped | skipped |
| JWK without `use` | usable | usable |

### Documentation and Examples

* `docs/authentication.md`: the modes, the validation rules, the limits on tokens, keys and JSON documents (D20, D22), that `typ` isn't checked (so both `JWT` and `at+jwt` work, and every Keycloak version Blaze supports works in RFC mode) and that `OPENID_PROVIDER_ISSUER` and `OPENID_AUDIENCE` are mandatory; the outdated "Additional Considerations" ("only the first public key is used") are corrected.
* `docs/deployment/environment-variables.md`: new `OPENID_LEGACY_MODE`; `OPENID_PROVIDER_ISSUER` and `OPENID_AUDIENCE` become mandatory unless legacy mode is on.
* The example setups `docs/authentication/docker-compose.yml` and `docs/deployment/full-standalone` (compose file and `keycloak/import/realm-blaze.json`) set issuer and audience.
* `docs/api/admin.md` (the only API page describing the 401 response today) and `docs/authentication.md` (for the FHIR API): the `WWW-Authenticate` header.
* New sections and variables get the badge `Since 1.15.0` for now (D16).
* `CHANGELOG.md` of the release: a `Notes` entry explaining the breaking change and `OPENID_LEGACY_MODE`.

### CI

* `.github/openid-auth-test`: every Blaze service sets issuer and audience; the wrong-`iss` and wrong-`aud` services keep testing rejection. A new service with `OPENID_LEGACY_MODE=true` and neither issuer nor audience checks that legacy mode starts and accepts tokens. The realm stays as it is, so Keycloak's default `typ: JWT` is covered.
* `.github/integration-test-extern-terminology-openid-auth` and the frontend test use Keycloak too and set issuer and audience, where they authenticate against Blaze.

#### Integration Test Against a Fake OpenID Provider (D25)

Keycloak can't produce most out-of-bounds documents (duplicate keys, an oversized or too deeply nested JWKS, 17 keys) nor tokens breaking the other verification rules, so a new, separate CI job `openid-auth-limits-test` runs Blaze against a **fake OpenID provider** serving prepared documents (D25). It runs after the image build, like `openid-auth-test`, which stays unchanged apart from the changes above.

* **Files:** `.github/openid-auth-limits-test/docker-compose.yml`; `.github/scripts/openid-auth-limits/generate.sh`, which creates keys, documents and tokens; `.github/scripts/openid-auth-limits/check.sh`, which sends the requests and checks the responses.
* **Fake provider:** an nginx container, pinned by digest, serving a directory of static files written by `generate.sh`. Every scenario is its own path, `/<scenario>/.well-known/openid-configuration`, whose `jwks_uri` points to `/<scenario>/jwks.json`. Its `issuer` is the scenario URL, which the Blaze services set as `OPENID_PROVIDER_ISSUER`, together with a fixed `OPENID_AUDIENCE`.
* **Keys and tokens:** `generate.sh` creates the RSA keys with `openssl genrsa` (`-3` for the e = 3 key), writes `n` and `e` as base64url from `openssl rsa -modulus`, and builds the tokens with `jq`, `basenc --base64url` and `openssl dgst -sha256 -sign`. It has two commands: `generate.sh documents` writes the keys and documents right before `docker compose up`, and `generate.sh tokens` signs the tokens right before `check.sh`. Starting the Blaze services takes longer than the 30 s margin of the leeway cases (`exp` 30 s ago, `nbf` in 30 s), so tokens signed before `docker compose up` would no longer be inside the leeway. Tokens are never printed to the log.
* **Blaze services**, each in RFC mode with in-memory storage and a small heap:

| Service | Provider scenario | Requests and expected responses |
|---|---|---|
| `blaze-mixed` | JWKS with the good key K1 plus keys to be skipped: 1024 bits, 8200 bits, e = 3, and 16 further good keys (17 usable in total) | token signed by K1: 200; by the 1024-bit key, the 8200-bit key, the e = 3 key and the 17th good key: 401 with `error="invalid_token"`. JWT JSON at the limits, signed by K1: header and payload nested 8 levels, a number of 32 digits, a name of 256 characters: 200; each one beyond (9 levels, 33 digits, 257 characters) and duplicate keys in header and in payload: 401 with `error="invalid_token"` |
| `blaze-jwks-too-large` | JWKS of 1 MiB plus one byte, padded with an unknown member | token signed by its only key: 503, since Blaze has no key (D27) |
| `blaze-jwks-too-deep` | JWKS whose key has a member nested 9 levels deep | token signed by its only key: 503, since Blaze has no key (D27) |
| `blaze-jwks-duplicate-keys` | JWKS whose key has `n` twice | token signed by its only key: 503, since Blaze has no key (D27) |
| `blaze-jwks-long-string` | JWKS whose key has an `x5c` string of 16 KiB plus one character | token signed by its only key: 503, since Blaze has no key (D27) |
| `blaze-config-too-large` | OpenID configuration of 1 MiB plus one byte | token: 503, since Blaze has no key (D27) |

* **Verification rules**, also on `blaze-mixed` (RFC mode), every token signed by K1 unless noted:

| Token | Expected |
|---|---|
| no `Authorization` header | 401, `WWW-Authenticate: Bearer realm="<issuer>"` without `error` |
| valid, `typ` `JWT` and `at+jwt`, `aud` as string and as array | 200 |
| header `alg` `none`, `HS256`, `rs256` with a valid RS256 signature; `crit` header | 401 |
| `alg` `HS256` with an HMAC signature using K1's public key bytes as secret; `alg` `none` with an empty signature | 401 |
| `jwk` header with an attacker's key, signed by that key | 401 |
| header `kid` of another key in the JWKS | 200 |
| a flipped signature byte; a flipped payload byte | 401 |
| `exp` missing; `exp` 120 s ago; `nbf` in 120 s | 401 |
| `exp` 30 s ago; `nbf` in 30 s (inside the 60 s leeway) | 200 |
| wrong `iss`; `aud` missing; `aud` array without the audience | 401 |

  All 401 responses to presented tokens carry `error="invalid_token"`.
* **`blaze-legacy`:** `OPENID_LEGACY_MODE=true` without issuer and audience, against the scenario of `blaze-mixed`. Tokens without `exp`, with header `alg` `none` and a valid RS256 signature, with `crit`, and with any `iss` and `aud`: 200. `exp` 30 s ago (no leeway in legacy mode): 401. A request without token: 401 with `WWW-Authenticate: Bearer realm="Blaze"`. The log contains the legacy-mode warning and today's `iss`/`aud` warnings.
  The services of the document-level scenarios also check the log for the fetch error, without key material in it. The 16 KiB string limit can't be reached through a token, since a token has at most 8192 characters; the unit tests and the JWKS scenario cover it. Exponents of 2^256 and more can't be generated with `openssl genrsa`, so only the unit tests cover them.

## Test Plan

Test keys are generated with `KeyPairGenerator` (RSA 2048, plus 1024 for the too-small case), and test tokens are signed by a test-only helper with `Signature` `SHA256withRSA`, so the tests don't need buddy. A known-answer test uses the RS256 example of RFC 7515 Appendix A.2 (fixed key, token and signature; verified in legacy mode with a clock before its `exp`, since it has no `aud`).

Unless noted, each case runs in both modes with the expected result per mode.

* **Valid:** minimal RFC claims; `typ` `JWT`, `at+jwt`, missing and a number, in both modes, to pin down that it isn't checked; `aud` as string and as array; `kid` matching one of several keys; no `kid` with several keys; RFC 7515 A.2 vector (legacy).
* **Mode differences:** `alg` `rs256`, `RS512`, missing, with a valid RS256 signature; `crit`; `exp` missing; `iss`/`aud` not configured (legacy only, since RFC mode doesn't start).
* **Startup:** RFC mode without issuer, without audience, without both fails with the expected messages; legacy mode without issuer, without audience and without both starts and logs today's warning for each missing variable, plus the legacy-mode warning.
* **Time:** `exp` = now, now − 1, inside and outside the leeway; `exp` a string; `nbf` in the future inside and outside the leeway; `iat` a string.
* **Claims:** wrong `iss`; `iss` missing while configured; wrong `aud`; `aud` missing; `aud` array without the value; `aud` array with a non-string; payload an array, a string, `null`.
* **Algorithm confusion (rejected by the signature check in both modes):** `alg` `none` with an empty signature (rejected by the shape check) and with a non-empty one; `HS256` with an HMAC signature using the RSA public key's encoded bytes as secret; `PS256` and `ES256` signatures.
* **Header injection (rejected in both modes):** `jwk` with the attacker's key and a signature by it; `jku`; `x5u`; `x5c`. The signature check fails, because these headers are never used.
* **Signature:** a header `kid` naming key A with a valid signature by key B (accepted, as today); a header `kid` unknown to the JWKS with a valid signature by one of the keys (accepted); a byte of the signature flipped; a byte of the payload flipped (re-encoded); signature truncated and extended by one byte; signature by an unrelated key; `kid` a number.
* **Shape:** empty string; 1, 2, 4 and 5 segments; empty segments; padding `=`; whitespace; `+` and `/`; non-ASCII; 8192 and 8193 characters; header that is not base64url or not JSON; duplicate header keys.
* **JWKS:** key of 1024 bits (skipped, so tokens by it are rejected); keys of 8192 and 8200 bits; `n` of 1366 and 1367 characters; `e` = 1, 3, 65536 (even), 65537, 2^256 − 1 and 2^256, and of 43 and 44 characters; 16 and 17 usable keys; JWKS and OpenID configuration of exactly 1 MiB and one byte more; `use: enc` (skipped); no `use`; `alg: RSA-OAEP` (skipped); `kty` `EC` and `oct` (skipped); missing or invalid `n`; private key members; empty set; the existing documents (`google.json`, one key, one `sig` + one `enc` key) and a Keycloak 26 JWKS.
* **`blaze.http-client.json`:** `object-mapper` applies each key of the constraints map (value at the limit parses, one above fails), keeps Jackson's default for a missing key, and rejects duplicate keys; `parse-json` returns an anomaly for invalid JSON.
* **JSON constraints**, for the strict mapper directly and through a token payload and a JWKS: nesting of 8 and 9 levels; numbers of 32 and 33 digits; names of 256 and 257 characters; strings of 16 KiB and one character more (`openid-auth`) and of 64 KiB and one more (`openid-client`); documents of 1 MiB and one byte more; a Keycloak 26 access token with `realm_access` and `resource_access` parses.
* **openid-client:** the existing tests pass without cheshire; an OpenID configuration and a token response of exactly 1 MiB and one byte more.
* **Contract:** `wrap-authentication` assocs `:identity` if the backend authenticates and `:blaze.auth/failure` otherwise; the backend returns the `incorrect` anomaly without credentials and the `forbidden` one with rejected credentials, both with the realm; `www-authenticate` for both failures, including a realm with characters needing no escaping; a system without `OPENID_PROVIDER_URL` has no backend, no `wrap-authentication` and no auth guards;
* **`WWW-Authenticate`:** both auth guards, without credentials and with rejected credentials, in both modes; the integration scripts check the header too.
* **Integration:** the `openid-auth-test` CI job against Keycloak, as described under CI.

## Decisions

* **D1 Spec-conformant default, legacy mode by flag.** The default validates as RFC 9068, RFC 8725 and RFC 7515 require from a resource server. `OPENID_LEGACY_MODE=true` keeps today's validation rules, without today's accidental behaviour. Legacy mode is deprecated from the start and planned for removal in the next major version.
* **D2 Algorithm fixed in both modes.** The verifier always uses RS256; the header never selects the algorithm. RFC mode additionally requires the header `alg` to be `RS256`, as RFC 8725 §3.1 demands; legacy mode doesn't look at it.
* **D3 `iss` and `aud` are mandatory in RFC mode.** Blaze doesn't start without them.
* **D4 Keycloak is the reference provider.**
* **D5 `typ` is not checked, in either mode.** This deviates from RFC 9068 §4 on purpose. Keycloak issues `typ: JWT` before 26.2 always, and from 26.2 on unless a per-client setting is enabled, and deployments still run Keycloak 24. Checking `typ` would stop an ID token from passing as an access token. In Blaze that gives no attacker anything today:
  * `aud` must contain `OPENID_AUDIENCE`, which is mandatory in RFC mode. So only ID tokens of the client whose ID equals `OPENID_AUDIENCE` pass; ID tokens of other clients don't.
  * In the documented setup that client is the frontend, which holds the access token of the same user anyway, keeps its tokens server-side in encrypted cookies, uses the authorization code flow, and disables provider logout, so no `id_token_hint` ends up in a URL.
  * Keycloak gives ID tokens the lifetime of access tokens.
  * Blaze doesn't authorize on claims or scopes, so an ID token grants nothing an access token wouldn't.

  The check has to be reconsidered when Blaze starts to authorize on scopes (e.g. SMART on FHIR), because an ID token carries no scopes, or if a deployment points `OPENID_AUDIENCE` to a client whose ID tokens appear in URLs (implicit flow, front-channel logout).
* **D6 New module `modules/auth`** for the protocol, `authenticate`, `authenticated?` and the middleware.
* **D7 `kid` is an optimization.** Keys with the token's `kid` are tried first, then all others, so which tokens are accepted doesn't change. RFC 7515 allows this: §4.1.4 calls `kid` "a hint", and Appendix D, step 3, describes that "keys referenced by `kid` … might be tried before keys with neither of these values".
* **D8 Token size limit** of 8192 characters, in both modes.
* **D9 Leeway** of 60 seconds in RFC mode, 0 in legacy mode.
* **D10 Clock.** The backend gets `:blaze/clock` as a new Integrant dependency.
* **D11 `openid-client` drops cheshire** in the same issue and commit.
* **D12 Issue.** One issue of type `Task`, "Remove buddy and BouncyCastle From Authentication", without milestone, with the module labels of the changed modules, including a new label for `auth`.
* **D13 Key headers ignored.** `jwk`, `jku`, `x5u` and `x5c` are ignored in both modes. Keys only ever come from the JWKS, which RFC 9068 §4 requires ("MUST use the keys provided by the authorization server"); RFC 7515 §6 notes that changing such headers "in a way that causes a different key to be used will cause the validation to fail"; RFC 8725 §3.10 only forbids following `jku`/`x5u` blindly.
* **D14 Minimum RSA key size 2048 bits**, in both modes. Keycloak's `rsa-generated` key provider defaults to 2048 bits (versions 24.0.0, 26.4.2 and `main`). NIST IR 8547 (final, October 2026) deprecates RSA with 112-bit security after 2030 and disallows it after 2035. BSI TR-02102-1 (2026-01) recommends a modulus of at least 3000 bits and RSA signatures until the end of 2035. Requiring 3000 bits would reject every Keycloak realm with default settings, so `docs/authentication.md` recommends configuring 3072 or 4096 bits in Keycloak instead. Raising the minimum, or adding PS256 (BSI recommends EMSA-PSS padding), is left to later issues.
* **D15 `WWW-Authenticate`** in this change, in both modes, as described above.
* **D16 Version badges** are `Since 1.15.0` for now and may change before the release.
* **D17 Realm.** The OpenID backend's realm is `OPENID_PROVIDER_ISSUER`, e.g. `WWW-Authenticate: Bearer realm="https://keycloak.example.com/realms/blaze"`. In legacy mode without an issuer it is `Blaze`. The issuer is public (it is in every token and in the provider's discovery document), unlike `OPENID_PROVIDER_URL`, which in container setups is often an internal address such as `http://keycloak:8080/realms/blaze`. Blaze fails to start if the issuer contains a `"`, a `\` or a control character, so the configuration can't break the header.

* **D18 A single backend.** Blaze has exactly one authentication backend, OpenID, so the collection of backends and its "first identity wins" logic go away. Another backend in the future would get its own design.

* **D19 Failures are anomalies carrying the realm.** `-authenticate` never returns `nil`:

  | Case | Return value |
  |---|---|
  | Valid token | the claims (identity) |
  | No `Authorization: Bearer …` header | anomaly, `::anom/category ::anom/incorrect`, `:blaze.auth/realm` |
  | Token rejected | anomaly, `::anom/category ::anom/forbidden`, `:blaze.auth/realm`, `:blaze.auth/error "invalid_token"` |
  | Token present, but no public keys available (D27) | anomaly, `::anom/category ::anom/unavailable`, `:blaze.auth/realm` |

  The realm is needed on every 401, including the one without credentials, so it travels with the failure instead of through a second protocol method.
* **D20 Bounded JWKS input.** The JWKS is input too: a compromised Keycloak, or anyone who can tamper with the fetch over plain HTTP, can serve any document. Such an attacker can sign valid tokens anyway, so the limits don't protect authentication. They bound Blaze's resources and so its availability, also against a merely broken JWKS. Every rejected token is checked against every key (D7), and an attacker can send such tokens for free. Measured on this JDK, a check with a 2048-bit key and e = 65537 costs about 0.09 ms, with a 3072-bit key and an exponent nearly as long as the modulus about 14.6 ms. The JDK limits the modulus to 16384 bits and, above 3072 bits, the exponent to 64 bits, but allows such exponents below. So:
  * `n` has at most 8192 bits (Keycloak offers up to 4096), `e` is in the FIPS 186-5 range 2^16 < e < 2^256 (Keycloak uses 65537);
  * the string lengths of `n` (1366) and `e` (43) are checked before decoding;
  * at most 16 keys are used (Keycloak usually publishes one or two signing keys, a few more during rotation);
  * the OpenID configuration and the JWKS document are each limited to 1 MiB.

  In the worst case, a rejected token costs 16 checks with an 8192-bit key and a small exponent, a few milliseconds.
* **D21 The 1 MiB limit applies to `openid-client` too**, for the OpenID configuration and the token response. `blaze.http-client.json` in the `http-client` module, which both modules already depend on, provides `object-mapper` and `parse-json`; each of the two modules defines its own mapper with its own values in its `impl` namespace. `maxDocumentLength` enforces the limit while the response stream is parsed, so no separate bounded reader is needed, provided the parser reads to the end of the input (D26). `http-client` gains a dependency on jsonista, which all its users already have on their classpath.
* **D22 Tight JSON constraints** as in the table under JSON: nesting 8, numbers 32 digits, names 256 characters, strings 16 KiB in `openid-auth` and 64 KiB in `openid-client`, documents 1 MiB. A custom Keycloak claim mapper emitting JSON deeper than 8 levels makes every token of that client fail; `docs/authentication.md` documents the limits.
* **D23 Constraints as a Clojure map.** `blaze.http-client.json/object-mapper` takes the constraints as a map of optional keys (`:max-nesting-depth`, `:max-number-length`, `:max-name-length`, `:max-string-length`, `:max-document-length`) and builds the `StreamReadConstraints` itself. A missing key keeps Jackson's default.
* **D24 Duplicate JSON keys are rejected, in both modes and in every document.** RFC 7515 §4 (header) and RFC 7519 §4 (claims) allow parsers either to reject duplicate member names or to return only the last one; producers MUST NOT emit them. Last-wins is Jackson's default and today's behaviour (cheshire), and since header and payload are both covered by the signature, duplicates in a valid token can only come from the signer. Blaze still rejects them with `STRICT_DUPLICATE_DETECTION`: no real token, JWKS or OpenID configuration contains duplicates, so rejecting them costs nothing; ambiguous input is refused instead of being resolved silently; and Blaze can't take part in a parser differential with another component that picks the first value (RFC 9413 argues against tolerance in security-relevant protocols).
* **D25 Integration test against a fake OpenID provider** in a separate CI job `openid-auth-limits-test`, covering the JSON and JWKS limits and the other verification rules in both modes, as described under CI. The fake provider is an nginx container serving static files.
* **D26 Trailing tokens are rejected.** Jackson checks `maxDocumentLength` only when it refills its input buffer (8000 bytes), so the bytes of the last buffer are never counted and a document of 1 MiB plus one byte parses. `object-mapper` therefore always enables `DeserializationFeature/FAIL_ON_TRAILING_TOKENS`, which makes the parser read on to the end of the input, where the final refill checks the whole length. This makes the limit exact and also rejects content after the JSON value, which no real document has.

* **D27 503 while no keys are available.** Before the first successful JWKS fetch, one second after the start, or as long as the provider is unreachable from the start, the backend has no keys and can't judge a token. Rejecting it with `invalid_token` would tell a client holding a valid token that the token is bad. So the backend returns an `unavailable` anomaly carrying the realm, and both auth guards answer `503 Service Unavailable` without `WWW-Authenticate` header: the FHIR guard with an `OperationOutcome` of code `transient`, the admin guard with `{"msg": "…"}`. Requests without credentials still get 401. A fetch without answer after a successful one keeps the previous keys, so this happens until the first success, and after a JWKS without usable key (D30).
* **D28 Skipped keys are logged at WARN only on change.** Keycloak always publishes an `RSA-OAEP` encryption key, so logging every skipped key at WARN on every fetch would warn every minute in every Keycloak deployment. `jwk/public-keys` is pure and returns the messages of the skipped keys; the backend keeps those of the last fetch and logs them at WARN only if they differ from the previous fetch, at DEBUG otherwise. A fetch without any usable key fails and still updates the skipped keys.
* **D29 `null` counts as absent for `exp` and `nbf` in legacy mode**, as it did with buddy, so legacy mode accepts the same tokens as before. In RFC mode a `null` `exp` or `nbf` is not a number and the token is rejected.
* **D30 A JWKS without usable key removes the previous keys.** A JWKS that is fetched and parsed is the provider's answer, whether it has usable keys or not. Keeping the previous keys only in the case of none would be inconsistent, since a JWKS with other usable keys replaces them, and it would defeat revocation: if a provider removes a compromised RSA key and publishes only, for example, an EC key, the compromised key would stay accepted until Blaze restarts. New tokens signed by the unusable key are rejected anyway, so keeping the old keys gains no availability. Blaze has no keys then, answers requests with a bearer token with 503 (D27) and logs the fetch error every minute. Only a fetch without a valid JWK Set (D31) keeps the previous keys. This changes the behaviour of today, which keeps the previous keys in both cases.
* **D31 Only a valid JWK Set replaces the keys.** A JWKS document is a valid JWK Set if it is a JSON object with a `keys` array whose elements are all JSON objects (RFC 7517 §5). A 2xx response with JSON that isn't a JWK Set, like `{}`, `null`, an array or an error object, is no answer about the keys: the fetch fails, logs an error and keeps the previous keys, as with an error status or invalid JSON. Treating it as a JWK Set without keys (D30) would let a provider in a broken state make Blaze answer every request with a bearer token with 503. `{"keys": []}` is a valid JWK Set and, per D30, removes the keys. Within a valid JWK Set, a JWK that is an object but unusable is skipped, as before.
* **D32 NumericDates are integers or finite doubles.** RFC 7519 §2 allows fractional NumericDates, so `exp`, `nbf` and `iat` accept any integer and any finite double. Jackson parses a number like `1e999`, which fits the 32-digit limit, as an infinite double, so `exp: 1e999` would never expire and `nbf: -1e999` would always be valid. The predicate `numeric-date?` rejects infinite values where these claims are checked. Jackson's global number handling and all other claims are left alone.

## Open Questions

None.
