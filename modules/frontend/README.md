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
