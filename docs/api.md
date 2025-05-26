# FHIR API

Blaze exposes a [FHIR RESTful API][1] under the default context path of `/fhir`. The [CapabilityStatement][2] exposed under `/fhir/metadata` can be used to discover the capabilities of Blaze. Everything stated there can be considered to be implemented correctly. If you find any discrepancies, please open an [issue][3]. 

## Interactions

### Instance Level

* [Read](api/interaction/read.md)
* [Versioned Read](api/interaction/vread.md)
* [Update](api/interaction/update.md)
* [Delete](api/interaction/delete.md)
* [Delete History](api/interaction/delete-history.md) <Badge type="warning" text="Since 0.30.1"/>
* [History](api/interaction/history-instance.md)

### Type Level

* [Create](api/interaction/create.md)
* [Delete](api/interaction/delete-type.md)
* [Search](api/interaction/search-type.md)
* [History](api/interaction/history-type.md)

### System Level

* [Capabilities](api/interaction/capabilities.md)
* [Transaction](api/interaction/transaction.md)
* [Batch](api/interaction/batch.md)
* [Search](api/interaction/search-system.md)
* [History](api/interaction/history-system.md)

## Operations

The following Operations are implemented:

* [$compact](api/operation/compact.md) <Badge type="warning" text="Since 0.31"/>
* [$graphql](http://hl7.org/fhir/resource-operation-graphql.html)
* [Measure $evaluate-measure](api/operation/measure-evaluate-measure.md)
* [Patient $everything](api/operation/patient-everything.md) <Badge type="warning" text="Since 0.22"/>
* [Patient $purge](api/operation/patient-purge.md) <Badge type="warning" text="Since 0.30.1"/>
* [CodeSystem $validate-code](api/operation/code-system-validate-code.md) <Badge type="warning" text="Since 0.32"/>
* [ValueSet $expand](api/operation/value-set-expand.md) <Badge type="warning" text="Since 0.32"/>
* [ValueSet $validate-code](api/operation/value-set-validate-code.md) <Badge type="warning" text="Since 0.32"/>

## Asynchronous Requests

Some requests like $evaluate-measure or complex FHIR searches with `_summary=count` can take longer as a typical HTTP request response cycle should be open. Typical HTTP request timeouts from client and intermediates are 30 seconds. Requests that take longer than that would require special handling. In oder to overcome that synchronous request handling, FHIR specifies [Asynchronous Request Patterns](http://hl7.org/fhir/R5/async.html).

Blaze implements the [Asynchronous Interaction Request Pattern][8] from FHIR R5.

### Example FHIR Search Request

To initiate a sync request, the HTTP header `Prefer` has to set to `respond-async`:

```sh
curl -svH 'Prefer: respond-async' "http://localhost:8080/fhir/Observation?code=http://loinc.org|8310-5&_summary=count" 
```

The response will look like this:

```text
HTTP/1.1 202 Accepted
Content-Location: http://localhost:8080/fhir/__async-status/DD7MLX6H7OGJN7SD
```

The status code 202 indicates that the request was accepted and will continue to be processed in the background. The `Content-Location` header contains an opaque URL to a status endpoint.

> [!NOTE]
> Please be aware that Blaze will respond synchronously if the response is available in time or the async handling isn't implemented yet. Clients always have to be able to handle synchronous responses as well.  

Polling that status endpoint:

```sh
curl -svH 'Accept: application/fhir+json' "http://localhost:8080/fhir/__async-status/DD7MLX6H7OGJN7SD" 
```

will either result in a intermediate result like this:

```text
HTTP/1.1 202 Accepted
X-Progress: in-progress
```

or in the final response:

```text
HTTP/1.1 200 OK
Content-Type: application/fhir+json;charset=utf-8
Content-Length: 412
```

The response itself will be a Bundle of type `batch-response`:

```json
{
  "resourceType": "Bundle",
  "id": "DD7MLX6JYN54OHKZ",
  "type": "batch-response",
  "entry": [
    {
      "response": {
        "status": "200"
      },
      "resource": {
        "resourceType": "Bundle",
        "id": "DD7MLX6JZHGD5YSA",
        "type": "searchset",
        "total": 1689,
        "link": [
          {
            "relation": "self",
            "url": "http://localhost:8080/fhir/Observation?code=http%3A%2F%2Floinc.org%7C8310-5&_summary=count&_count=50"
          }
        ]
      }
    }
  ]
}
```

### Cancelling an Async Request

Async requests can be cancelled before they are completed:

```sh
curl -svXDELETE "http://localhost:8080/fhir/__async-status/DD7MLX6H7OGJN7SD"
```

## Paging Sessions

Interactions and operations that return a large list of resources support paging via Bundle resources. The various Bundle resources are interlinked via the next link. The process of retrieving a part of or all Bundle resources (pages) of a large response forms a paging session. Paging sessions have the following properties: 

### Stable

Paging sessions operate on a stable database snapshot. Next links will point to custom paging session endpoints. The endpoints will expire after for 4 hours in order to constrain the usage of a paging session. That also means that clients which have access to a paging session, will be able to access deleted and changed resources for up to 4 hours.

### Expire

Paging sessions will expire after 4 hours without activity. Activities are requesting the first or next page.

### Fast

Paging sessions don't require initial setup time and show constant costs per page in most cases. In fact paging sessions don't require book keeping at server side at all. Their state is solely communicated via link URLs. For search requests with more than one parameter, page costs can vary because of internal query handling.

### Encrypted

The variable part of paging URLs is encrypted to ensure confidentiality and integrity of the paging parameters. Confidentiality is important in case some of the original query parameters contain sensitive information. To mitigate the risk of exposing this data, FHIR searches are often executed via POST requests, which helps prevent sensitive information from being logged in URLs. Consequently, it is essential that paging URLs do not reveal any confidential data. Integrity is important, because it should not be possible to manipulate the paging URL in order to access a different paging session.

#### Encryption Key Management

<dl>
  <dt>Key Rotation</dt>
  <dd>Encryption keys are rotated every two hours. Each key is valid for a maximum of four hours, with a total of three keys stored at any time.</dd>
  <dt>Storage</dt>
  <dd>Currently, encryption keys are stored in plain text within the admin database. While these keys are not accessible via an API, they are also not encrypted with an external key encryption method.</dd>
  <dt>Future Improvements</dt>
  <dd>Implementing external key encryption is feasible but would require additional infrastructure. If you believe that key encryption is necessary, please open an issue for further discussion.</dd>
</dl>

## Absolute URLs

Blaze has to generate absolute URLs of its own in links and Location headers. By default Blaze assumes to be accessible under `http://localhost:8080`. The [environment variable](deployment/environment-variables.md) `BASE_URL` can be used to change this.

Besides the static `BASE_URL` setting, Blaze also respects the reverse proxy headers X-Forwarded-Host, X-Forwarded-Proto and [Forwarded][4] to generate its base URL dynamically.

[1]: <https://hl7.org/fhir/R4/http.html>
[2]: <https://hl7.org/fhir/R4/capabilitystatement.html>
[3]: <https://github.com/samply/blaze/issues>
[4]: <https://datatracker.ietf.org/doc/html/rfc7239>
[5]: <https://hl7.org/fhir/http.html#capabilities>
[6]: <https://semver.org>
[7]: <https://en.wikipedia.org/wiki/Coordinated_Universal_Time>
[8]: <http://hl7.org/fhir/R5/async-bundle.html>
