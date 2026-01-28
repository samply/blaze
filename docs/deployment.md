# Deployment

## Quick Start

Blaze can be started with a single command using docker:

```sh
docker run -d --name blaze -p 8080:8080 samply/blaze:1.4
```

## Verification <Badge type="warning" text="Since 1.0" />

For container images, we use [cosign][1] to sign images. This allows users to confirm the image was built by the
expected CI pipeline and has not been modified after publication.

```sh
cosign verify "samply/blaze:1.4.1" \
  --certificate-identity-regexp "https://github.com/samply/blaze/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  --certificate-github-workflow-ref="refs/tags/v1.4.1" \
  -o text
```

The expected output is:

```text
Verification for index.docker.io/samply/blaze:1.4.1 --
The following checks were performed on each of these signatures:
  - The cosign claims were validated
  - Existence of the claims in the transparency log was verified offline
  - The code-signing certificate was verified using trusted certificate authority certificates
Certificate subject: https://github.com/samply/blaze/.github/workflows/build.yml@refs/tags/v1.4.1
Certificate issuer URL: https://token.actions.githubusercontent.com
GitHub Workflow Trigger: push
GitHub Workflow SHA: 79937e53c48b5966bc8774feec98e1708980d73f
GitHub Workflow Name: Build
GitHub Workflow Repository: samply/blaze
GitHub Workflow Ref: refs/tags/v1.4.1
```

This output ensures that the image was build on the GitHub workflow on the repository `samply/blaze` and tag `v1.4.1`.

## Production

For production-ready deployments, there are three options:

* [Frontend and Backend Standalone](deployment/full-standalone.md)
* [Standalone Backend Only](deployment/standalone-backend.md)
* [Distributed Backend Only](deployment/distributed-backend.md)

> [!important]
> Also see the [Production Configuration](./production-configuration.md) guide.

## Configuration

Configuration is based on environment variables and documented in the [Configuration](deployment/environment-variables.md) section.

[1]: <https://docs.sigstore.dev/cosign/signing/overview/>
