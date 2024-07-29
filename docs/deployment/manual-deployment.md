# Manual Deployment

The installation works under Windows, Linux and macOS. The only dependency is an installed OpenJDK 11 or 17 with 17 recommended. Blaze is tested with [Eclipse Temurin][1].

Blaze runs on the JVM and comes as single JAR file. Download the most recent version [here](https://github.com/samply/blaze/releases/tag/v0.29.1). Look for `blaze-0.29.1-standalone.jar`.

After the download, you can start blaze with the following command (Linux, macOS):

```sh
java -jar blaze-0.29.1-standalone.jar
```

Blaze will run with an in-memory, volatile database for testing and demo purposes.

Blaze can be run with durable storage by setting the environment variables `STORAGE` to `standalone`. 

Under Linux/macOS:

```sh
STORAGE=standalone java -jar blaze-0.29.1-standalone.jar
```

Under Windows, you need to set the Environment variables in the PowerShell before starting Blaze:

```powershell
$Env:STORAGE="standalone"
java -jar blaze-0.29.1-standalone.jar
```

This will create three directories called `index`, `transaction` and `resource` inside the current working directory, one for each database part used.

The output should look like this:

```text
2021-06-27T11:02:29.649Z ee086ef908c1 main INFO [blaze.system:173] - Set log level to: info
2021-06-27T11:02:29.662Z ee086ef908c1 main INFO [blaze.system:43] - Try to read blaze.edn ...
2021-06-27T11:02:29.679Z ee086ef908c1 main INFO [blaze.system:152] - Use storage variant standalone
2021-06-27T11:02:29.680Z ee086ef908c1 main INFO [blaze.system:163] - Feature OpenID Authentication disabled
...
2021-06-27T11:02:37.758Z ee086ef908c1 main INFO [blaze.system:230] - Start metrics server on port 8081
2021-06-27T11:02:37.822Z ee086ef908c1 main INFO [blaze.system:218] - Start main server on port 8080
2021-06-27T11:02:37.834Z ee086ef908c1 main INFO [blaze.core:64] - JVM version: 16.0.2
2021-06-27T11:02:37.834Z ee086ef908c1 main INFO [blaze.core:65] - Maximum available memory: 1738 MiB
2021-06-27T11:02:37.835Z ee086ef908c1 main INFO [blaze.core:66] - Number of available processors: 8
2021-06-27T11:02:37.836Z ee086ef908c1 main INFO [blaze.core:67] - Successfully started Blaze version 0.29.1 in 8.2 seconds
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
  "version": "0.29.1"
}
```

Blaze will be configured through environment variables which are documented [here][2].

[1]: <https://adoptium.net>
[2]: <environment-variables.md>
