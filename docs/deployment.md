<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const tag = release.replace('v', '');
</script>

# Deployment

## Quick Start

Blaze can be started with a single command using docker:

```sh-vue
docker run -d --name blaze -p 8080:8080 samply/blaze:{{ tag }}
```

## Verification <Badge type="warning" text="Since 1.0" />

For container images, we use [cosign][1] to sign images. This allows users to confirm the image was built by the
expected CI pipeline and has not been modified after publication.

```sh-vue
cosign verify "samply/blaze:{{ tag }}" \
  --certificate-identity-regexp "https://github.com/samply/blaze/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  --certificate-github-workflow-ref="refs/tags/{{ release }}" \
  -o text
```

The expected output is:

```text-vue
Verification for index.docker.io/samply/blaze:{{ tag }} --
The following checks were performed on each of these signatures:
  - The cosign claims were validated
  - Existence of the claims in the transparency log was verified offline
  - The code-signing certificate was verified using trusted certificate authority certificates
Certificate subject: https://github.com/samply/blaze/.github/workflows/build.yml@refs/tags/{{ release }}
Certificate issuer URL: https://token.actions.githubusercontent.com
GitHub Workflow Trigger: push
GitHub Workflow SHA: <sha>
GitHub Workflow Name: Build
GitHub Workflow Repository: samply/blaze
GitHub Workflow Ref: refs/tags/{{ release }}
```

This output ensures that the image was build on the GitHub workflow on the repository `samply/blaze` and tag `{{ release }}`.

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
