<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const digest = import.meta.env.VITE_LATEST_DIGEST;
  const tag = release.substring(1);
</script>

# External Validator <Badge type="info" text="Feature: EXTERN_VALIDATOR_URL"/> <Badge type="warning" text="Since 1.11.0"/>

When the external validator is enabled, every resource of an incoming
[create](../api/interaction/create.md), [update](../api/interaction/update.md)
or [transaction/batch](../api/interaction/transaction.md) interaction is
forwarded to an external FHIR validator for schema and profile validation
**before** it is persisted. This allows Blaze to reject or flag resources that do
not conform to the configured implementation guides, without embedding a
validator into the server itself.

Any validator that exposes the `validateResource` endpoint can be used. The
reference validator for this feature is the [MII FHIR Validator][1].

The external validator performs the profile validation and needs its own
terminology server to resolve `ValueSet` bindings. This terminology server does
**not** have to be Blaze — any FHIR terminology server works, for example
[Ontoserver][4] or a second Blaze instance with an enabled
[Terminology Service](../terminology-service.md).

## Feature Toggle

The feature is a toggle that is enabled by setting the base URL of the external
validator:

| Environment Variable   | Default | Description                             |
|------------------------|---------|-----------------------------------------|
| `EXTERN_VALIDATOR_URL` | —       | Base URL of an external FHIR validator. |

Setting this variable enables external profile validation. Every incoming
resource is forwarded to `<EXTERN_VALIDATOR_URL>/validateResource`. If the
variable is not set, the feature stays disabled and resources are persisted
without external validation.

## The `validateResource` Endpoint

Blaze sends each resource as a `POST` request to the `validateResource` endpoint
of the external validator and expects an [OperationOutcome][2] in return:

```
POST <EXTERN_VALIDATOR_URL>/validateResource
Content-Type: application/fhir+json

<the resource to validate>
```

A resource is considered **invalid** if the returned OperationOutcome contains at
least one issue with severity `error` or `fatal`. Issues of severity `warning` or
`information` do not mark a resource as invalid.

## Failure Modes

The failure mode controls what happens when a resource fails external validation.
It is set through the `VALIDATOR_FAILURE_MODE` environment variable:

| Environment Variable     | Default       | Description                                 |
|--------------------------|---------------|---------------------------------------------|
| `VALIDATOR_FAILURE_MODE` | `tag-outcome` | One of `tag-only`, `tag-outcome`, `reject`. |

* **`tag-only`** — store the invalid resource, but add a tag with system
  `https://blaze-server.org/fhir/CodeSystem/ValidationStatus` and code `invalid`
  to its `meta`.
* **`tag-outcome`** (default) — like `tag-only`, but additionally store the
  validator's OperationOutcome as a [contained][3] resource referenced from a
  `meta` extension with URL
  `https://blaze-server.org/fhir/StructureDefinition/validation-outcome`. The
  contained resource's id is opaque and not fixed — locate the OperationOutcome
  by following the extension's reference, not by assuming a well-known id.
* **`reject`** — reject the resource with `400 Bad Request` and an
  OperationOutcome describing the validation issues. The resource is not stored.

Valid resources are always stored unchanged, regardless of the failure mode.

> [!IMPORTANT]
> If the external validator is unreachable, the write is always rejected with
> `503 Service Unavailable` (fail closed). Blaze never silently skips validation
> once the feature is enabled.

### Finding Invalid Resources

With the tagging failure modes, invalid resources can be found across all
resource types through a system-wide `_tag` search:

```sh
curl -s 'http://localhost:8080/fhir?_tag=https://blaze-server.org/fhir/CodeSystem/ValidationStatus|invalid'
```

With `tag-outcome`, the reason a resource was flagged is available in a
contained OperationOutcome. Locate it by following the reference in the
resource's `meta` extension (URL
`https://blaze-server.org/fhir/StructureDefinition/validation-outcome`); the
contained resource's id is opaque and not fixed, so do not rely on it.

## Concurrency

To protect a validator that cannot handle many parallel requests, the number of
validation requests sent concurrently is bounded. Requests beyond this limit are
queued until a slot becomes available.

| Environment Variable               | Default | Description                                              |
|------------------------------------|---------|----------------------------------------------------------|
| `EXTERN_VALIDATOR_MAX_CONCURRENCY` | `4`     | Maximum number of concurrent requests to the validator.  |

Lower this value to protect a slow validator; raise it to increase throughput
when the validator can handle more load.

## TLS

If the external validator is served over HTTPS with a certificate that is not
trusted by the default Java trust store, a custom PKCS #12 trust store can be
supplied:

| Environment Variable                       | Default | Description                                      |
|--------------------------------------------|---------|--------------------------------------------------|
| `EXTERN_VALIDATOR_CLIENT_TRUST_STORE`      | —       | A PKCS #12 trust store with the needed CA certs. |
| `EXTERN_VALIDATOR_CLIENT_TRUST_STORE_PASS` | —       | The password for the PKCS #12 trust store.       |

The PKCS #12 trust store has to be generated by the Java keytool. OpenSSL will
not work.

```sh
keytool -importcert -storetype PKCS12 -keystore "trust-store.p12" \
  -storepass "..." -alias ca -file "cert.pem" -noprompt
```

## Example Deployment with the MII FHIR Validator

The [MII FHIR Validator][1] is a ready-to-run validation service that exposes the
`validateResource` endpoint. It needs a terminology server (`TX_SERVER`) to resolve
ValueSet bindings. As noted above, this terminology server can be any FHIR
terminology server; the example below uses a second Blaze instance, but pointing
`TX_SERVER` at an [Ontoserver][4] or a public terminology server works just as
well.

The resulting setup consists of three services:

* **`blaze`** — the main data server that forwards incoming resources to the
  validator.
* **`fhir-validator`** — the MII FHIR Validator, pointing its terminology server
  (`TX_SERVER`) at the terminology server.
* **`terminology-server`** — the terminology server used by the validator, here a
  Blaze instance with an enabled terminology service.

```yaml-vue
services:
  blaze:
    image: "samply/blaze:{{ tag }}@{{ digest }}"
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx2g"
      EXTERN_VALIDATOR_URL: "http://fhir-validator:8080"
      VALIDATOR_FAILURE_MODE: "tag-outcome"
    ports:
    - "8080:8080"
    volumes:
    - "blaze-data:/app/data"
    depends_on:
      fhir-validator:
        condition: service_started

  fhir-validator:
    image: "ghcr.io/medizininformatik-initiative/mii-fhir-validator:latest"
    environment:
      JAVA_OPTS: "-Xmx2g"
      TX_SERVER: "http://terminology-server:8080/fhir"
    ports:
    - "8084:8080"
    volumes:
    - "./fhir-settings.json:/app/fhir-settings.json:ro"
    depends_on:
      terminology-server:
        condition: service_healthy

  terminology-server:
    image: "samply/blaze:{{ tag }}@{{ digest }}"
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx8g"
      ENABLE_TERMINOLOGY_SERVICE: "true"
      ENABLE_TERMINOLOGY_LOINC: "true"
      ENABLE_TERMINOLOGY_SNOMED_CT: "true"
      SNOMED_CT_RELEASE_PATH: "/app/sct-release"
    ports:
    - "8082:8080"
    volumes:
    - "blaze-terminology-data:/app/data"
    - "./sct-release:/app/sct-release"
    healthcheck:
      test: [ "CMD", "wget", "--spider", "http://localhost:8080/health" ]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

volumes:
  blaze-data:
  blaze-terminology-data:
```

The MII FHIR Validator reads its terminology configuration from a
`fhir-settings.json` file, pointing it at the terminology server:

```json
{
  "servers": [
    {
      "url": "http://terminology-server:8080/fhir",
      "type": "fhir",
      "authenticationType": "none",
      "allowHttp": true
    }
  ]
}
```

> [!NOTE]
> The SNOMED CT release file has to be uncompressed into the `sct-release`
> directory mounted into the terminology server. See the
> [Terminology Service](../terminology-service.md) section for details on
> providing the SNOMED CT release.
>
> To use an [Ontoserver][4] or another terminology server instead of a second
> Blaze instance, drop the `terminology-server` service and point both
> `TX_SERVER` and the `url` in `fhir-settings.json` at that server.

With this setup, sending a resource to `http://localhost:8080/fhir` triggers
validation against the configured implementation guides. Non-conforming resources
are tagged as `invalid` (and, with `tag-outcome`, carry the validator's
OperationOutcome), while conforming resources are stored as usual.

## Metrics

Blaze exposes the latency of external validation requests through the Prometheus
histogram `blaze_validator_extern_request_duration_seconds`. See the
[Monitoring](../monitoring.md) section for how to scrape Blaze's metrics.

## Configuration Reference

All environment variables of this feature are listed in the
[Configuration](../deployment/environment-variables.md) section.

[1]: <https://github.com/medizininformatik-initiative/mii-fhir-validator>
[2]: <https://www.hl7.org/fhir/operationoutcome.html>
[3]: <https://www.hl7.org/fhir/references.html#contained>
[4]: <https://ontoserver.csiro.au/>
