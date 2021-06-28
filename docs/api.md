# FHIR RESTful API

Blaze exposes a [FHIR RESTful API][1] under the default context path of `/fhir`. The [CapabilityStatement][2] exposed under `/fhir/metadata` can be used to discover the capabilities of Blaze. Everything stated there can be considered to be implemented correctly. If not please [file an issue][3]. 

## Absolute URLs

Blaze has to generate absolute URLs of its own in links and Location headers. By default Blaze assumes to be accessible under `http://localhost:8080`. The [environment variable](deployment/environment-variables.md) `BASE_URL` can be used to change this.

Besides the static `BASE_URL` setting, Blaze also respects the reverse proxy headers X-Forwarded-Host, X-Forwarded-Proto and [Forwarded][4] to generate its base URL dynamically.

[1]: <https://www.hl7.org/fhir/http.html>
[2]: <https://www.hl7.org/fhir/capabilitystatement.html>
[3]: <https://github.com/samply/blaze/issues>
[4]: <https://datatracker.ietf.org/doc/html/rfc7239>
