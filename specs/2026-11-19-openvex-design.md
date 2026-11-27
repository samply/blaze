# OpenVEX Statements for Blaze Images

- Start Date: 2026-11-19
- Status: Accepted
- Issues: #4732

## Summary

Blaze publishes its analyses of vulnerabilities that can't be exploited in its images as `not_affected` statements in a single OpenVEX document in the repository. Operators pass it to Trivy or Grype to suppress these findings, the CI scan applies it as well, and a CI job checks that both scanners keep applying it.

## Motivation

Vulnerability scanners flag advisories of dependencies shipped in the Blaze images even when the vulnerable code can't be reached in Blaze. An example is GHSA-x4c4-68gp-vrhc, a Clojure deserialization RCE that requires `ObjectInputStream.readObject` to be called on attacker-controlled bytes, which nothing in Blaze or on its runtime classpath does. Today every operator has to repeat such an analysis or add a local ignore rule. Blaze publishes its analyses as VEX (Vulnerability Exploitability eXchange) statements instead, so scanners suppress these findings and operators can see why.

### Goals

* State in a machine-readable form that a Blaze image is `not_affected` by a vulnerability of one of its components, with a justification and the analysis behind it.
* Suppress findings covered by such a statement in the Trivy scan of the CI, so the GitHub Security tab only shows findings that still need action.
* Let operators apply the statements with Trivy and Grype, both for new and for already-released images.
* Make adding a statement a reviewable change of a single file.
* Check in CI that both scanners keep applying the statements.

### Non-Goals

* No `affected` statements. An exploitable vulnerability is fixed, and if it is in Blaze itself, published as a GitHub Security Advisory.
* No automatic pickup by scanners. Operators download the document and pass it to their scanner. The following can be added later on top of the same document:
  * registering the images in Trivy's VEX Hub, whose crawler reads the `.vex/` directory of the source repository (`trivy image --vex repo`);
  * attaching VEX attestations for Docker Scout.
* No image attestations. An attestation is bound to an image digest, so statements written after a release would only reach that release by re-attesting it. Trivy uses the first OpenVEX attestation in registry order and doesn't merge them, which makes retrofitting unreliable.
* No SBOM publication.
* No change to `docs/osv-scanner.toml`. It covers the npm dependencies of the documentation generator, which isn't part of any image.
* No statements for the npm dependencies of the frontend. SvelteKit bundles them into the JavaScript of the app, so the frontend image holds no `package.json` or `node_modules` and scanners can't identify them. They're checked in the repository by osv-scanner (`modules/frontend/osv-scanner.toml`) instead.
* No statements for vulnerabilities of the Alpine OpenSSL packages (`libssl3`, `libcrypto3`) of the frontend image for now. The Node.js process doesn't load them, but Node.js has its own OpenSSL of the same version built in, so the vulnerable code is likely still in the process. A statement would need an analysis of which OpenSSL features Node.js uses.

## Guide-Level Explanation

An operator scanning a Blaze image downloads the current document before each scan and passes it to the scanner:

```sh
curl -fsSLO https://raw.githubusercontent.com/samply/blaze/main/.vex/blaze.openvex.json
trivy image --vex blaze.openvex.json samply/blaze:<tag>@<digest>
```

Findings covered by a statement are suppressed. `--show-suppressed` lists them, and the impact statement of each links the GitHub issue with the analysis.

A contributor who analysed a vulnerability and found it not exploitable in Blaze adds a statement to the document, linking the GitHub issue that holds the analysis. A contributor changing code that could reach the vulnerable code of a statement revises or removes that statement.

## Reference-Level Explanation

### Document

* There is a single document `.vex/blaze.openvex.json` for all images. The `.vex/` directory at the repository root and the `*.openvex.json` name are the ones the VEX Hub crawler looks for, so a later registration needs no move.
* Its `@id` is `https://github.com/samply/blaze/.vex/blaze.openvex.json`, and its `author` is `Blaze maintainers`.
* Each change increments `version` and sets `last_updated`, as OpenVEX requires.
* It's edited by hand. vexctl may be used, but isn't required.

### Statements

* **Products:** the image once per registry, as an OCI PURL with the percent-encoded `repository_url` qualifier and without a version:
  * `pkg:oci/blaze?repository_url=index.docker.io%2Fsamply%2Fblaze`
  * `pkg:oci/blaze?repository_url=ghcr.io%2Fsamply%2Fblaze`

  The same applies to `blaze-frontend`. Trivy and Grype both match these against `samply/blaze:<tag>` and `ghcr.io/samply/blaze:<tag>` (checked with Blaze 1.11.0 and Blaze Frontend 1.13.0), and VEX Hub requires this form.
* **Subcomponents:** the vulnerable package as a PURL with its version, e.g. `pkg:maven/com.google.guava/guava@33.7.1-jre` or `pkg:apk/alpine/zlib@1.3.2-r1`. A statement lists one subcomponent per analysed version.
  * The PURL has no qualifiers. Trivy and Grype report OS packages with different qualifiers (`arch`, `distro`, `upstream`), and `arch` also differs between the image platforms. Both scanners match a PURL without qualifiers against any qualifiers (checked with Blaze Frontend 1.13.0).
  * The image is never the only product, because that would suppress the vulnerability in every package of the image.
  * The package is never the product, because the claim is about Blaze, not about the package.
  * Pinning the version limits the statement to the analysed version. A dependency upgrade that still carries the vulnerability needs a new analysis.
* **Vulnerability:** `name` is the identifier the scanners report, which is the CVE if there is one and the GHSA otherwise. Further identifiers go into `aliases`.
* **Status:** always `not_affected`, with one of the CISA justifications. The usual ones for Blaze are:
  * `vulnerable_code_not_in_execute_path`
  * `vulnerable_code_cannot_be_controlled_by_adversary`
  * `vulnerable_code_not_present`
* **Impact statement:** an `impact_statement` summarises the analysis in a few sentences and links the GitHub issue holding the full analysis.
* **Lifetime:** a statement isn't removed when the dependency is upgraded, because the older images it covers are still in use.

### Initial Content

The statements cover the releases that were analysed, Blaze 1.10.0 to 1.13.0, backend and frontend. Older releases aren't covered, and operators running them are expected to upgrade.

#### Backend

Both backend statements rest on the same analysis, done for the runtime classpath of each covered release and of `main` (in #4732): neither Blaze nor any library on its runtime classpath calls `ObjectInputStream.readObject` on data from the network, a database or Kafka.

* All wire and storage formats are JSON, XML or CBOR.
* The library classes constructing an `ObjectInputStream` are never called. These are commons-lang3 `SerializationUtils`, the commons-io validating streams, netty's `ObjectDecoder` and Jetty's `ObjectStreamSessionDataStore`. In Blaze 1.10.0 and 1.11.0 they also include javassist's `javassist.tools.rmi` classes and `ProxyObjectInputStream`, pulled in by msgpack through transit, which no class outside javassist references.

The statements:

1. **GHSA-x4c4-68gp-vrhc**, Clojure deserialization RCE.
   * **Subcomponents:** `pkg:maven/org.clojure/clojure@1.12.5` (Blaze 1.11.0 to 1.13.0) and `pkg:maven/org.clojure/clojure@1.12.4` (Blaze 1.10.0), as read from the `pom.properties` in the uberjar of each image. These releases don't pin Clojure in the root `deps.edn`, so the default of the Clojure CLI that built them won over the version in `modules/module-base/deps.edn`.
   * **Justification:** `vulnerable_code_not_in_execute_path`.
   * The advisory has no CVE yet and isn't in the scanner databases yet. Once a CVE is assigned, it becomes the `name` and the GHSA moves to `aliases`.
2. **CVE-2026-102554** (alias GHSA-xxph-c9ww-hj94), uncontrolled memory allocation when Guava collections are deserialized.
   * **Subcomponents:** `pkg:maven/com.google.guava/guava@33.7.1-jre` (Blaze 1.11.0) and `pkg:maven/com.google.guava/guava@33.6.0-jre` (Blaze 1.10.0).
   * **Justification:** `vulnerable_code_not_in_execute_path`.

#### Frontend

The frontend image is `node:26-alpine` plus the SvelteKit build. Scanners only see its Alpine packages.

3. **CVE-2026-4738**, a heap corruption in the optional `infback9` code of the zlib `contrib` directory (`inftree9.c`). Alpine attributes it to its `zlib` package, fixed in `1.4-r0`.
   * **Subcomponents:** `pkg:apk/alpine/zlib@1.3.2-r1` (Blaze Frontend 1.12.0 to 1.13.0) and `pkg:apk/alpine/zlib@1.3.2-r0` (Blaze Frontend 1.10.0 and 1.11.0).
   * **Justification:** `vulnerable_code_not_present`. The `libz.so.1.3.2` of every covered release, on amd64 and arm64, exports the standard `inflateBack` functions but no `infback9` functions and holds no `inftree9` code.
   * Independently of that, the `node` binary doesn't load `libz`, because Node.js has its own zlib built in. In the image, only `apk` uses `libz`.
   * The `node` binary of every covered release (Node.js 24.17.0 to 26.13.0) holds two zlib copies, its own and the one of V8 (`Cr_z_` prefix). Its full symbol table has neither `infback9` functions nor `inflate_table9`, so the vulnerable code isn't compiled into Node.js either.

### Rules for Adding a Statement

* Every statement needs a GitHub issue with the analysis.
* The versions of the subcomponents are taken from the released images, e.g. from an SBOM, never from the dependency declarations of the build, which can differ.
* A release a statement covers is added to the releases of its image in the `vex-test` job.
* A change adding a code path that could reach the vulnerable code of a statement must remove or revise that statement. Reviewers check `.vex/` when a change touches deserialization, class loading or evaluation of code.

### CI Scan

* The Trivy step of the `image-scan` job gets `TRIVY_VEX: .vex/blaze.openvex.json`. The job already checks out the repository.
* Suppressed findings aren't written to the SARIF file, so they don't appear in the GitHub Security tab.
* The job currently scans the image pulled from GHCR and retagged as `blaze:latest`. If the statements don't match that local name, the job scans the GHCR reference instead.

### CI Check

A new job `vex-test` in `.github/workflows/build.yml` checks that the published statements are applied:

* It's a matrix over both images. Each scans a release by digest, once from Docker Hub and once from GHCR:
  * Blaze 1.11.0, which ships Guava 33.7.1-jre, for CVE-2026-102554;
  * Blaze Frontend 1.13.0, which ships zlib 1.3.2-r1, for CVE-2026-4738.
* For each image and scanner it scans once with `.vex/blaze.openvex.json` and asserts that the vulnerability is listed as suppressed by VEX and not as a finding. Both scanners list suppressed findings separately, so a suppressed finding proves both that the scanner still detects the vulnerability and that the statement matches it. A finding vanishing from a scanner database fails the check instead of passing it without testing anything.
* Trivy runs with its default settings.
* For the backend, Grype runs with `GRYPE_MATCH_JAVA_USING_CPES=true`. Its GitHub advisory data describes the affected range as `<=33.7.1`, and Maven ordering puts `33.7.1-jre` after `33.7.1`, so Grype misses the finding with its default settings. With CPE matching it reports the finding against the same Guava PURL, which is enough to test that it applies the statement. A comment in the job states this.
* The scans cover one subcomponent per image, and the Clojure advisory isn't in the scanner databases yet. So the job also checks every subcomponent without a scanner: from an SBOM of every covered release, generated by Trivy, it collects the PURLs without qualifiers and asserts that each subcomponent of the image occurs in at least one of them. This catches a wrong package name or version in any statement. It can't catch a release that ships an affected version the statement doesn't list, because it doesn't know the affected range.
* The job also validates the document against the JSON schema of the OpenVEX spec with `check-jsonschema`. The spec has no releases, so the schema is pinned to a commit and its checksum. The schema only requires the IDs to be strings, so the job further checks that:
  * every product is one of the four product PURLs (two images on two registries),
  * every product has subcomponents, and every subcomponent is a PURL with a version and without qualifiers,
  * every vulnerability name and alias is a CVE or GHSA ID,
  * every statement lists both registries of an image with the same subcomponents.
* Trivy and Grype are installed from their release archives with pinned versions and checksums, like the other tools of the workflow, so Renovate keeps them up to date.

### Documentation

A new section "Vulnerability Scanning" in `docs/deployment.md`, after "Verification", badged with the version of the issue's milestone, describes:

* that a statement means Blaze maintainers analysed the vulnerability and found it not exploitable in Blaze, and where the analyses are,
* that the document covers all released images and grows over time, so it's downloaded again before each scan:

  ```sh
  curl -fsSLO https://raw.githubusercontent.com/samply/blaze/main/.vex/blaze.openvex.json
  ```

* how to scan the backend and the frontend image with it, in two tabs like the "Verification" section, using the image reference with digest like the rest of the page:

  ```sh
  trivy image --vex blaze.openvex.json samply/blaze:<tag>@<digest>
  ```

  ```sh
  grype samply/blaze:<tag>@<digest> --vex blaze.openvex.json
  ```

* that `--show-suppressed` lists the suppressed findings with both scanners, and that Grype can load the document on every scan via the `vex-documents` config key.

## Drawbacks

* Scanners don't pick up the statements automatically. Operators have to download the document and pass it to their scanner.
* Statements are pinned to the analysed versions of a package, so every dependency upgrade that still carries a vulnerability needs a new analysis.
* A statement can become wrong when code changes, and only review catches that.
* Releases before Blaze 1.10.0 aren't covered.

## Rationale and Alternatives

### Format

1. **OpenVEX v0.2.0 (chosen).** A small JSON format, written by hand and read by Trivy for image scans, by Grype, by Docker Scout and by vexctl. The spec is still labelled a draft and v1 is in early drafts, but v0.2.0 has been stable for years. go-vex also reads CSAF, so a later switch stays cheap.
2. **CSAF 2.0 VEX profile.** An OASIS standard that Trivy and Grype also read, but a document needs a full product tree and publisher metadata, which is a lot of ceremony for a handful of statements. Docker Scout doesn't read it.
3. **CycloneDX VEX.** Trivy applies CycloneDX VEX only to SBOM scans, not to image scans, and Grype doesn't read it.
4. **`.trivyignore` plus prose in the docs.** Works for the CI only, is specific to one scanner and has no notion of a justification or of the product a statement is about.

### Distribution

1. **A single document in the repository, downloaded by operators (chosen).** The document on `main` is the source of truth. A new statement reaches every released image at once, and both Trivy (`--vex`) and Grype (`--vex` or `vex-documents`) read local files.
2. **Image attestations.** See the non-goals.
3. **Trivy VEX Hub.** Fully automatic for Trivy users passing `--vex repo`, but it needs a registration in a third-party crawler, isn't enabled by default in Trivy and doesn't help Grype. It can be added later without changing the document, see the non-goals.

### CI Check

1. **Scan a pinned release that has a finding covered by a real statement (chosen).** It tests the statements operators actually use, and the finding stays in the scanner databases because the image never changes.
2. **A test-only document with a statement made up for a known finding.** Tests the mechanism, but not the published statements, and a document that doesn't hold an analysis must not end up in `.vex/`, where the VEX Hub crawler would publish it.
3. **Rely on the scan of the current image.** Current images usually have no finding a statement covers, so the check would pass without testing anything.

## Prior Art

None.

## Unresolved Questions

None.

## Future Possibilities

* Registering the images in Trivy's VEX Hub and attaching VEX attestations for Docker Scout, both on top of the same document (see Non-Goals).
* Statements for the Alpine OpenSSL packages of the frontend image, after an analysis of which OpenSSL features Node.js uses.

## Verification

* Both `vex-test` jobs pass, and each fails when its statement (Guava or zlib) is removed from the document.
* The subcomponent check fails when a subcomponent version is changed to one no covered release ships, e.g. `pkg:maven/org.clojure/clojure@1.12.6`.
* The document check fails for a product ID like `not an iri`, a subcomponent without a version or with qualifiers, a malformed CVE or GHSA ID, and a statement missing a registry or listing different subcomponents per registry.
* The `image-scan` job passes with `TRIVY_VEX` set.
* `npm run docs:build` succeeds with the new section.
