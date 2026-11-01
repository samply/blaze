# Blaze Frontend

## Developing

First run `make clean build` to install all NPM dependencies and build the frontend.

Second, start Keycloak:

```sh
docker compose up --wait
```

Third, run the frontend in dev mode:

```sh
npm run dev
```

To develop without Keycloak, set `ENABLE_AUTH=false` in `.env`. The admin section isn't available then, and the backend
has to run without `OPENID_PROVIDER_URL`.

## Element Types

The Normal view of a resource needs the types of its top-level elements, for example to format dates or to link canonicals. They are generated from the StructureDefinitions of the FHIR definitions of the fhir-structure module into the virtual module `virtual:element-types` at build time, so `make build-fhir-structure` has to run before `npm run dev`, `npm run build` and `npm run test:unit`. `make build` and `make test` do that.
