<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const digest = import.meta.env.VITE_LATEST_DIGEST;
  const tag = release.substring(1);
</script>

# Deployment

## Quick Start

Blaze can be started with a single command using docker:

```sh-vue
docker run -d --name blaze -p 8080:8080 samply/blaze:{{ tag }}@{{ digest }}
```

## Verification <Badge type="warning" text="Since 1.0" />

For container images, we use [cosign][1] to sign images. This allows users to confirm the image was built by the
expected CI pipeline and has not been modified after publication. 

> [!NOTE]
> Make sure to use the image digest. **Tags alone are mutable and can be updated to point to different images**. Pinning to the digest (the `@sha256:` part) ensures you use the exact build intended for a given release.

```sh-vue
cosign verify "samply/blaze:{{ tag }}@{{ digest }}" \
  --certificate-identity-regexp "https://github.com/samply/blaze/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  --certificate-github-workflow-ref="refs/tags/{{ release }}" \
  -o text >/dev/null
```

The expected output is:

<<< @/cosign-verify.txt {text}

This output ensures that the image was built by the GitHub Actions workflow of the repository `samply/blaze` and tag `{{ release }}`.

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
