# Sync Resources from Another FHIR Server to Blaze

## Using Subscriptions

If you want to facilitate the CQL engine or other features of Blaze, but you can't or don't like to use Blaze as your primary FHIR server, you can configure your primary FHIR server to automatically sync every change to Blaze by using the [subscription][1] mechanism.

In this example we use [HAPI][2] as our primary FHIR server. In the `docs/data-sync` directory, you can find a Docker Compose file with a setup of a HAPI and a Blaze server. Please start the containers by running:

```sh
docker-compose -f docs/data-sync/subscription/docker-compose.yml up
```

After both servers are up and running, you can create two subscriptions, one for Patient resources and one for Observations. Please run:

```sh
curl -H 'Content-Type: application/fhir+json' -d @subscription-bundle.json http://localhost:8090/fhir
```

After you created the subscriptions, you can import or change data on the HAPI server, and it will be synced automatically to the Blaze server.

Because the subscription mechanism doesn't send the resources in the right order to satisfy referential integrity, Blaze is started with `ENFORCE_REFERENTIAL_INTEGRITY` set to `false`.


## Create a Full Clone of a Blaze Server

Another use-case would be to copy all data from one Blaze server to another. That can be useful to either:
 
* remove the history from a Blaze server,
* create a snapshot of all resources,
* migrate from Blaze to another FHIR server or the other way around.

### Setup Test Environment

In order to test copying all data from one Blaze server to another, start the following Docker Compose project:

```sh
docker-compose -f docs/data-sync/copy/docker-compose.yml up
```

You should see a `src` server started at port 8080 and a `dst` server started at port 8082.

### Load Data into the Source Server

Next, load some data into the source server:

```sh
blazectl upload --server http://localhost:8080/fhir .github/test-data/synthea
```

After that finishes, you can use `blazectl count-resources` to ensure that the source server has data and the destination server hasn't:

```sh
blazectl count-resources --server http://localhost:8080/fhir
blazectl count-resources --server http://localhost:8082/fhir
```

### Copy All Resources from Source to Destination

The `copy-data.sh` script uses [GNU Parallel][3]. You may have to install that first.

```sh
scripts/copy-data.sh http://localhost:8080/fhir http://localhost:8082/fhir
```

The script outputs `Successfully send transaction bundle` for each transaction bundle send to the destination server.

You can use `blazectl count-resources` to see whether the resource counts of the destination server equals the resource counts of the source server:

```sh
blazectl count-resources --server http://localhost:8080/fhir
blazectl count-resources --server http://localhost:8082/fhir
```

You can also compare the resource contents between the source and the destination server by downloading all resources (of a type), removing the `Meta.versionId` and `Meta.lastUpdated` values that will be different on the destination server:

```sh
blazectl download --server http://localhost:8080/fhir Patient | jq -c 'del(.meta.versionId) | del(.meta.lastUpdated)' > src-patients.ndjson 
blazectl download --server http://localhost:8082/fhir Patient | jq -c 'del(.meta.versionId) | del(.meta.lastUpdated)' > dst-patients.ndjson 
diff src-patients.ndjson dst-patients.ndjson
```

### Save All Resources from the Source Server

If you don't like to copy the data into the destination server immediately, you can also save the transaction bundles on disk and use `blazectl upload` later to upload them to the destination server.

```sh
mkdir dst
scripts/save-data.sh http://localhost:8080/fhir dst
```

[1]: <https://www.hl7.org/fhir/subscription.html>
[2]: <https://hapifhir.io>
[3]: <https://www.gnu.org/software/parallel/>
