# FHIR RESTful API

Blaze exposes a [FHIR RESTful API][1] under the default context path of `/fhir`. The [CapabilityStatement][2] exposed under `/fhir/metadata` can be used to discover the capabilities of Blaze. Everything stated there can be considered to be implemented correctly. If not please [file an issue][3]. 

## Interactions

### Update

Blaze keeps track over the history of all updates of each resource. However if the content of the resource update is equal to the current version of the resource, no new history entry is created. Usually such identical content updates will only cost a very small amount of transaction handling storage but no additional resource or index storage.

### Delete

By default Blaze enforces referential integrity while deleting resources. So resources that are referred by other resources can't be deleted without deleting the other resources first. That behaviour can be changed by setting the [environment variable](deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

### Search Type

#### _profile

Search for `Resource.meta.profile` is supported using the `_profile` search param with exact match or using the `below` modifier as in `_profile:below` with major and minor version prefix match. [Semver][6] is expected for version numbers so a search value of `<url>|1` will find all versions with major version `1` and a search value of `<url>|1.2` will find all versions with major version `1` and minor version `2`. Patch versions are not supported with the `below` modifier.

#### Date Search

When searching for date/time with a search parameter value without timezone like `2024` or `2024-02-16`, Blaze calculates the range of the search parameter values based on [UTC][7]. That means that a resource with a date/time value staring at `2024-01-01T00:00:00+01:00` will be not found by a search with `2024`. Please comment on [issue #1498](https://github.com/samply/blaze/issues/1498) if you like to have this situation improved.

#### Sorting

The special search parameter `_sort` supports the values `_id`, `_lastUpdated` and `-_lastUpdated`.

### Capabilities

Get the capability statement for Blaze. Blaze supports filtering the capability statement by `_elements`. For more information, see: [FHIR - RESTful API - Capabilities][5]

## Operations

The following Operations are implemented:

* [$graphql](http://hl7.org/fhir/resource-operation-graphql.html)
* [Measure $evaluate-measure](api/operation-measure-evaluate-measure.md)
* [Patient $everything](api/operation-patient-everything.md)

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

## Absolute URLs

Blaze has to generate absolute URLs of its own in links and Location headers. By default Blaze assumes to be accessible under `http://localhost:8080`. The [environment variable](deployment/environment-variables.md) `BASE_URL` can be used to change this.

Besides the static `BASE_URL` setting, Blaze also respects the reverse proxy headers X-Forwarded-Host, X-Forwarded-Proto and [Forwarded][4] to generate its base URL dynamically.

[1]: <https://www.hl7.org/fhir/http.html>
[2]: <https://www.hl7.org/fhir/capabilitystatement.html>
[3]: <https://github.com/samply/blaze/issues>
[4]: <https://datatracker.ietf.org/doc/html/rfc7239>
[5]: <https://hl7.org/fhir/http.html#capabilities>
[6]: <https://semver.org>
[7]: <https://en.wikipedia.org/wiki/Coordinated_Universal_Time>
[8]: <http://hl7.org/fhir/R5/async-bundle.html>
