# Docker Deployment

Blaze comes as a web application which needs one Docker volume to store its data.

## Volume

```sh
docker volume create blaze-data
```

## Blaze

```sh
docker run -d --name blaze -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.23
```

Blaze should log something like this:

```text
2023-06-09T08:30:21.134Z b45689460ff3 main INFO [blaze.system:181] - Set log level to: info
2023-06-09T08:30:21.155Z b45689460ff3 main INFO [blaze.system:44] - Try to read blaze.edn ...
2023-06-09T08:30:21.173Z b45689460ff3 main INFO [blaze.system:160] - Use storage variant standalone
2023-06-09T08:30:21.174Z b45689460ff3 main INFO [blaze.system:171] - Feature OpenID Authentication disabled
...
2023-06-09T08:30:30.079Z b45689460ff3 main INFO [blaze.server:33] - Start main server on port 8080
2023-06-09T08:30:30.122Z b45689460ff3 main INFO [blaze.server:33] - Start metrics server on port 8081
2023-06-09T08:30:30.126Z b45689460ff3 main INFO [blaze.core:67] - JVM version: 17.0.7
2023-06-09T08:30:30.126Z b45689460ff3 main INFO [blaze.core:68] - Maximum available memory: 1738 MiB
2023-06-09T08:30:30.126Z b45689460ff3 main INFO [blaze.core:69] - Number of available processors: 2
2023-06-09T08:30:30.126Z b45689460ff3 main INFO [blaze.core:70] - Successfully started 🔥 Blaze version 0.23.4 in 9.0 seconds
```

In order to test connectivity, query the health endpoint:

```sh
curl http://localhost:8080/health
```

After that please note that the [FHIR RESTful API](https://www.hl7.org/fhir/http.html) is available under `http://localhost:8080/fhir`. A good start is to query the [CapabilityStatement](https://www.hl7.org/fhir/capabilitystatement.html) of Blaze using [jq](https://stedolan.github.io/jq/) to select only the software key of the JSON output:

```sh
curl -H 'Accept:application/fhir+json' -s http://localhost:8080/fhir/metadata | jq .software
```

that should return:

```json
{
  "name": "Blaze",
  "version": "0.23.4"
}
```

Blaze will be configured through environment variables which are documented [here](environment-variables.md).

## Docker Compose

A Docker Compose file looks like this:

```text
services:
  blaze:
    image: "samply/blaze:0.23"
    environment:
      BASE_URL: "http://localhost:8080"
      JAVA_TOOL_OPTIONS: "-Xmx2g"
    ports:
    - "8080:8080"
    volumes:
    - "blaze-data:/app/data"
volumes:
  blaze-data:
```

## Custom Search Parameters

Per default, Blaze supports FHIR Search on all FHIR R4 search parameters. However Blaze can be configured to support custom search parameters by specifying the file name of a search parameter bundle in the environment variable `DB_SEARCH_PARAM_BUNDLE`. If such a bundle file name is specified, Blaze will index newly written resources using the search parameters defined in that file. Please be aware that Blaze will currently not reindex existing resources. So resources written before specifying a custom search parameter will not be indexed and so will not be found.

### Example Config

```text
services:
  blaze:
    image: "samply/blaze:0.23"
    environment:
      DB_SEARCH_PARAM_BUNDLE: "/app/custom-search-parameters.json"
    ports:
    - "8080:8080"
    volumes:
    - "custom-search-parameters.json:/app/custom-search-parameters.json:ro"
    - "blaze-data:/app/data"
volumes:
  blaze-data:
```
