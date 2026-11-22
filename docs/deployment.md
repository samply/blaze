<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const digest = import.meta.env.VITE_LATEST_DIGEST;
  const frontendDigest = import.meta.env.VITE_LATEST_FRONTEND_DIGEST;
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

::: code-group

```sh-vue [backend]
cosign verify "samply/blaze:{{ tag }}@{{ digest }}" \
  --certificate-identity-regexp "https://github.com/samply/blaze/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  --certificate-github-workflow-ref="refs/tags/{{ release }}" \
  -o text >/dev/null
```

```sh-vue [frontend]
cosign verify "samply/blaze-frontend:{{ tag }}@{{ frontendDigest }}" \
  --certificate-identity-regexp "https://github.com/samply/blaze/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  --certificate-github-workflow-ref="refs/tags/{{ release }}" \
  -o text >/dev/null
```

:::

The expected output is:

::: code-group

<<< @/cosign-verify.txt {text} [backend]
<<< @/cosign-verify-frontend.txt {text} [frontend]

:::

This output ensures that the image was built by the GitHub Actions workflow of the repository `samply/blaze` and tag `{{ release }}`.

## Vulnerability Scanning <Badge type="warning" text="Since 1.14.0" />

Vulnerability scanners sometimes report vulnerabilities of libraries and packages shipped in the backend and frontend images that can't be exploited in Blaze, because Blaze never runs the vulnerable code or the code isn't there at all. For such vulnerabilities, we publish [VEX][2] (Vulnerability Exploitability eXchange) statements that tell scanners to suppress the finding. A statement means that the Blaze maintainers analysed the vulnerability and found it not exploitable in Blaze. Each statement contains a justification and links to its analysis.

The statements are part of the [OpenVEX][3] document [`.vex/blaze.openvex.json`][4] in the Blaze repository. The document covers all released backend and frontend images, and new statements are added over time, so download it again before each scan:

```sh
curl -fsSLO https://raw.githubusercontent.com/samply/blaze/main/.vex/blaze.openvex.json
```

Then pass it to [Trivy][5] or [Grype][6] when scanning an image:

::: code-group

```sh-vue [backend]
# Trivy
trivy image --vex blaze.openvex.json "samply/blaze:{{ tag }}@{{ digest }}"

# Grype
grype "samply/blaze:{{ tag }}@{{ digest }}" --vex blaze.openvex.json
```

```sh-vue [frontend]
# Trivy
trivy image --vex blaze.openvex.json "samply/blaze-frontend:{{ tag }}@{{ frontendDigest }}"

# Grype
grype "samply/blaze-frontend:{{ tag }}@{{ frontendDigest }}" --vex blaze.openvex.json
```

:::

Both scanners list the suppressed findings, together with the statements suppressing them, if you add `--show-suppressed`. Grype can also load the document on every scan by setting the `vex-documents` key in its configuration file.

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
[2]: <https://www.cisa.gov/resources-tools/resources/minimum-requirements-vulnerability-exploitability-exchange-vex>
[3]: <https://github.com/openvex/spec>
[4]: <https://github.com/samply/blaze/blob/main/.vex/blaze.openvex.json>
[5]: <https://trivy.dev>
[6]: <https://github.com/anchore/grype>
