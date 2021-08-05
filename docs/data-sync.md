# Sync Resources from Another FHIR Server to Blaze

If you want to facilitate the CQL engine or other features of Blaze, but you can't or don't like to use Blaze as your primary FHIR server, you can configure your primary FHIR server to automatically sync every change to Blaze by using the [subscription][1] mechanism.

In this example we use [HAPI][2] as our primary FHIR server. In the `docs/data-sync` directory, you can find a Docker Compose file with a setup of a HAPI and a Blaze server. Please start the containers by running:

```sh
docker-compose up
```

after both servers are up and running, you can create two subscriptions, one for Patient resources and one for Observations. Please run:

```sh
curl -H 'Content-Type: application/fhir+json' -d @subscription-bundle.json http://localhost:8090/fhir
```

After you created the subscriptions, you can import or change data on the HAPI server, and it will be synced automatically to the Blaze server.

One problem, you may encounter is that if you issue transactions against HAPI with resources referencing each other, HAPI will not send them in the right order to Blaze, so that Blaze is complaining about violated referential integrity.


[1]: <https://www.hl7.org/fhir/subscription.html>
[2]: <https://hapifhir.io>
