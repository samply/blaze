# Docker Deployment

Blaze comes as a web application which needs one Docker volume to store its data.

## Volume

```bash
docker volume create blaze-data
```

## Blaze

```bash
docker run -d --name blaze -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.10.0-alpha.6
```

Blaze should log something like this:

```text
20-06-23 08:06:24 73c1e4c03d80 INFO [blaze.system:157] - Set log level to: info
2020-06-23 08:06:24.373+0000 73c1e4c03d80 INFO [blaze.system:41] - Try to read blaze.edn ...
2020-06-23 08:06:24.412+0000 73c1e4c03d80 INFO [blaze.system:147] - Feature RocksDB Key-Value Store enabled
2020-06-23 08:06:24.412+0000 73c1e4c03d80 INFO [blaze.system:147] - Feature In-Memory, Volatile Key-Value Store disabled
2020-06-23 08:06:24.413+0000 73c1e4c03d80 INFO [blaze.system:147] - Feature OpenID Authentication disabled
2020-06-23 08:06:24.430+0000 73c1e4c03d80 INFO [blaze.system:72] - Loading namespaces ...
...
2020-06-23 08:06:49.047+0000 73c1e4c03d80 INFO [blaze.system:217] - Start metrics server on port 8081
2020-06-23 08:06:49.223+0000 73c1e4c03d80 INFO [blaze.system:205] - Start main server on port 8080
2020-06-23 08:06:49.235+0000 73c1e4c03d80 INFO [blaze.core:60] - JVM version: 11.0.6
2020-06-23 08:06:49.236+0000 73c1e4c03d80 INFO [blaze.core:61] - Maximum available memory: 1488 MiB
2020-06-23 08:06:49.236+0000 73c1e4c03d80 INFO [blaze.core:62] - Number of available processors: 4
2020-06-23 08:06:49.238+0000 73c1e4c03d80 INFO [blaze.core:63] - Successfully started Blaze version 0.10.0-alpha.6 in 24.9 seconds
```

In order to test connectivity, query the health endpoint:

```bash
curl http://localhost:8080/health
```

After that please note that the [FHIR RESTful API](https://www.hl7.org/fhir/http.html) is available under `http://localhost:8080/fhir`. A good start is to query the [CapabilityStatement](https://www.hl7.org/fhir/capabilitystatement.html) of Blaze using [jq](https://stedolan.github.io/jq/) to select only the software key of the JSON output:

```bash
curl -H 'Accept:application/fhir+json' -s http://localhost:8080/fhir/metadata | jq .software
```

that should return:

```javascript
{
  "name": "Blaze",
  "version": "0.10.0-alpha.6"
}
```

Blaze will be configured through environment variables which are documented [here](environment-variables.md).

## Docker Compose

A Docker Compose file looks like this:

```text
version: '3.2'
services:
  blaze:
    image: "samply/blaze:0.10.0-alpha.6"
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

