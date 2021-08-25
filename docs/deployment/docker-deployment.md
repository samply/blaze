# Docker Deployment

Blaze comes as a web application which needs one Docker volume to store its data.

## Volume

```sh
docker volume create blaze-data
```

## Blaze

```sh
docker run -d --name blaze -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.11
```

Blaze should log something like this:

```text
2021-06-27T11:02:29.649Z ee086ef908c1 main INFO [blaze.system:173] - Set log level to: info
2021-06-27T11:02:29.662Z ee086ef908c1 main INFO [blaze.system:43] - Try to read blaze.edn ...
2021-06-27T11:02:29.679Z ee086ef908c1 main INFO [blaze.system:152] - Use storage variant standalone
2021-06-27T11:02:29.680Z ee086ef908c1 main INFO [blaze.system:163] - Feature OpenID Authentication disabled
...
2021-06-27T11:02:37.758Z ee086ef908c1 main INFO [blaze.system:230] - Start metrics server on port 8081
2021-06-27T11:02:37.822Z ee086ef908c1 main INFO [blaze.system:218] - Start main server on port 8080
2021-06-27T11:02:37.834Z ee086ef908c1 main INFO [blaze.core:64] - JVM version: 15.0.2
2021-06-27T11:02:37.834Z ee086ef908c1 main INFO [blaze.core:65] - Maximum available memory: 1738 MiB
2021-06-27T11:02:37.835Z ee086ef908c1 main INFO [blaze.core:66] - Number of available processors: 8
2021-06-27T11:02:37.836Z ee086ef908c1 main INFO [blaze.core:67] - Successfully started Blaze version 0.11.1 in 8.2 seconds
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
  "version": "0.11.1"
}
```

Blaze will be configured through environment variables which are documented [here](environment-variables.md).

## Docker Compose

A Docker Compose file looks like this:

```text
version: '3.2'
services:
  blaze:
    image: "samply/blaze:0.11"
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
