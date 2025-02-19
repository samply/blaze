# Deployment

## Quick Start

Blaze can be started with a single command using docker:

```sh
docker run -d --name blaze -p 8080:8080 samply/blaze:latest
```

## Production

For production ready deployments, there are three options:

* [Frontend and Backend Standalone](deployment/full-standalone.md)
* [Standalone Backend Only](deployment/standalone-backend.md)
* [Distributed Backend Only](deployment/distributed-backend.md)

## Configuration

Configuration is based on environment variables and documented in the [Configuration](deployment/environment-variables.md) section.
