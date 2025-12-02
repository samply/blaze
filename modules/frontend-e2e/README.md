# Frontend End-2-End Tests

## Run Production Tests

### Prepare

In the root project dir, run:

```sh
make clean
make build-job-ig
make build-all
```

In this module dir:

```sh
make install
make -C test-data install
make install-playwright
docker compose up -d
./upload.sh
```

### Run Tests

```sh
make test
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
