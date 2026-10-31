# Frontend End-2-End Tests

## Run Production Tests

### Prepare

In the root project dir, run:

```sh
make clean
make build-ig
make build-all
```

In this module dir:

```sh
make install
make -C test-data install
make install-playwright
docker compose up --wait
./upload.sh
```

### Run Tests

```sh
make test
```

## Run Production Tests Without Login

The tests in `src/no-auth` run against a deployment of the frontend without login (`ENABLE_AUTH=false`). Because that
deployment needs a backend without authentication, it can't run at the same time as the deployment above.

### Prepare

In the root project dir, run the same build steps as above, plus `make build-ingress` for the certificates of the
ingress. In this module dir:

```sh
make install
make install-playwright
docker compose -f docker-compose-no-auth.yml up --wait
./upload-no-auth.sh
```

### Run Tests

```sh
make test-no-auth
```

## Run Dev Tests

Run test in dev mode:

```sh
make test-dev
```

Run test in dev mode with UI:

```sh
make test-dev-ui
```

Run the tests without login in dev mode, with the frontend dev server started with `ENABLE_AUTH=false`:

```sh
make test-dev-no-auth
```
