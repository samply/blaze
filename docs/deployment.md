# Deployment

## Quick Start

Blaze can be started with a single command using docker:

```sh
docker run -d --name blaze -p 8080:8080 samply/blaze:1.0
```

## Verification <Badge type="warning" text="Since 1.0" />

For container images, we use [cosign][cosign] to sign images. This allows users to confirm the image was built by the
expected CI pipeline and has not been modified after publication.

```sh
cosign verify "samply/blaze:1.0" \
  --certificate-identity-regexp "https://github.com/samply/blaze/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  --certificate-github-workflow-ref="refs/tags/v1.0.0"
```

## Production

For production ready deployments, there are three options:

* [Frontend and Backend Standalone](deployment/full-standalone.md)
* [Standalone Backend Only](deployment/standalone-backend.md)
* [Distributed Backend Only](deployment/distributed-backend.md)

## Configuration

Configuration is based on environment variables and documented in the [Configuration](deployment/environment-variables.md) section.
