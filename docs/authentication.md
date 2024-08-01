# Authentication

Blaze comes with a build-in feature to authenticate requests against an [OpenID Connect][1] provider. In order to activate this feature, the environment variable `OPENID_PROVIDER_URL` has to be set to the base URL of your OpenID Connect provider.

If this feature is activated, all FHIR Endpoints will require a valid [JWT][2] in the [Authorization][3] header as `Bearer` token. The tokens are validated using the first public key available in the OpenID Connect configuration fetched from `<OPENID_PROVIDER_URL>/.well-known/openid-configuration`. Currently only RSA 256 signed tokens are supported. 

## Usage

In order to test the authentication feature, please start first [Keycloak][4] and then Blaze as defined in the Docker Compose file in the `docs/authentication` directory:

```sh
docker compose up keycloak
```

wait until keycloak is started

```sh
docker compose up blaze
```

after both services are up, please run:

```sh
ACCESS_TOKEN=$(./fetch-token.sh) ./request-all-resources.sh
```

The output should be:

```json
{
  "resourceType": "Bundle",
  "id": "C6IJYWHRYMGMXUFH",
  "type": "searchset",
  "total": 0,
  "link": [
    {
      "relation": "self",
      "url": "http://localhost:8080/fhir?_count=50&__t=0"
    }
  ]
}
```

## Additional Considerations

* Blaze will fetch the first public available under `<OPENID_PROVIDER_URL>/.well-known/openid-configuration` at the start and every minute afterwards
* only the first public key is used (please file an [issue][5] if you need more than the first key)
* the only RSA 256 signatures are supported (please file an issue if you need also RSA 512)

[1]: <https://openid.net/connect/>
[2]: <https://tools.ietf.org/html/rfc7519>
[3]: <https://tools.ietf.org/html/rfc7235#section-4.2>
[4]: <https://www.keycloak.org>
[5]: <https://github.com/samply/blaze/issues>
