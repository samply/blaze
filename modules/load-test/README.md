# Load Tests

Load tests use [k6][1].

## Run Locally

You need to [install k6][2].

```sh
k6 run script.js
```

## Run via Docker

```sh
docker run --rm -i grafana/k6 run - < script.js
```

## Results

Please look into the [Load Testing](../../docs/performance/load-testing.md) section of the docs.

[1]: <https://k6.io>
[2]: <https://grafana.com/docs/k6/latest/set-up/install-k6/>
