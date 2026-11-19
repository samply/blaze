# Canonical URL Resolution

## Intent

Blaze emits canonical URLs rooted at `https://blaze-server.org/fhir`, like `https://blaze-server.org/fhir/StructureDefinition/Job`. The Implementation Guide defining them is served under `/fhir` as part of the documentation site on GitHub Pages, but the publisher renders every resource as a flat file, `StructureDefinition-Job.html`, so every canonical URL returns 404. Following a canonical should return the definition, as FHIR JSON or XML for a program and as the rendered page for a browser.

## Goals

* Every canonical URL in the guide resolves, with content negotiation on `Accept` and the `_format` parameter.
* Beyond the issue's promise of canonicals: every resource of the guide answers at its FHIR read URL `[base]/[type]/[id]` with base `https://blaze-server.org/fhir`. The publisher renders `<Type>-<id>.html`, `.json` and `.xml` for every resource, canonical or not (the Task and Bundle examples, the NamingSystem), so this costs nothing extra, and relative references between the examples resolve too. A canonical is the case where `url` equals that read URL.
* Nothing to maintain by hand. Which paths exist and which files answer them follows from the guide, at the same commit as the served rendering.
* The routing logic is code in this repository, with tests.
* It fits the free plans of Cloudflare Workers and the zone.

## Non-Goals

* Canonicals of past releases. A canonical is version-independent; a client needing an exact version resolves it from the FHIR package.
* `https://blaze-server.org/fhir/sid/JobNumber`, the `uniqueId` of the JobNumber NamingSystem. It's an identifier system, not a resource path.
* Search, history or any other FHIR interaction besides read.
* Moving the documentation site off GitHub Pages (#4195).

## Approaches

1. **Cloudflare Worker on per-type routes (chosen).** A small Worker answers `/fhir/<Type>/<id>`, fetches the matching file from GitHub Pages and answers in place.
2. **Cloudflare Rules.** URL Rewrite Rules plus Response Header Transform Rules. Possible on the free plan: `http.request.headers`, `contains`, `wildcard` and `wildcard_replace()` are available, and `_format` precedence works through rule order, because later rewrites overwrite earlier ones. Rejected because rules can't parse q-values (a browser sends `application/xml;q=0.9` and must get HTML), `wildcard` ignores slashes so every rule needs exclusions, it takes about 8 of the 10 free rules, and the configuration lives in the dashboard without version control or tests.
3. **Rename the rendering into the canonical layout.** Rejected: an artifact page carries hundreds of relative references to `tbl_*.png` sprites, `assets/` and sibling pages, which break when a page moves one directory down, after every publisher release.
4. **Generated redirect stub pages.** Work on static hosting, but can't negotiate on `Accept`.
5. **A redirect to the flat page for every format.** Rejected: for a program the canonical is the identity, so FHIR JSON and XML are answered in place. HTML is redirected though, see below.

## Design

### Index

A generator reads every resource in `modules/ig/fsh-generated/resources` and writes an index from read path to the rendered file of every format:

```json
{
  "basePath": "/fhir",
  "resources": {
    "StructureDefinition/Job": {
      "html": "StructureDefinition-Job.html",
      "json": "StructureDefinition-Job.json",
      "xml": "StructureDefinition-Job.xml"
    },
    "ImplementationGuide/blaze": {
      "html": "index.html",
      "json": "ImplementationGuide-blaze.json",
      "xml": "ImplementationGuide-blaze.xml"
    }
  }
}
```

* The key is `<resourceType>/<id>`, relative to `basePath`, the path of the canonical base URL.
* Every format is recorded explicitly, so the Worker never derives a file name. `ImplementationGuide/blaze` is the one exception in the rendering: it has `.json` and `.xml`, but its HTML page is `index.html`.
* The generator fails if a resource with a `url` doesn't have `url == "https://blaze-server.org/fhir/" + resourceType + "/" + id`. So the index covers every canonical by construction, and a canonical that deviates from the convention fails the build instead of returning 404 in production.
* A check after the render fails if a file the index names is missing from the rendering, in every format.

The index is a build artifact, not committed.

### Routes

The Worker runs on one route per resource type, `blaze-server.org/fhir/<Type>/*`, 10 today. Not on `/fhir/*`:

* A Worker runs before the Cloudflare cache, so every request on its route counts against the free limit of 100,000 requests a day, cached or not. On `/fhir/*` every asset of the guide would count — a profile page references about 50 — which is about 100–200 requests per ten-page visit. With per-type routes, pages, assets and `package.tgz` never reach the Worker, and the limit only counts dereferenced resources.
* Route patterns allow no infix wildcards, so excluding assets from `/fhir/*` isn't possible anyway.

The routes are derived from the index at deploy time and passed to `wrangler deploy` as `--route` flags, so a new resource type gets its route with the deploy that ships it. With a token that covers all zones of the account, wrangler replaces the Worker's routes as a whole, so a type that disappears loses its route too.

### Request Handling

The Worker is a pure core and a thin handler:

* `negotiate(accept, format)` returns `json`, `xml` or `html`.
* `resolve(index, pathname)` returns the files of the resource or nothing.
* The handler takes the index, the origin and the `fetch` function as dependencies, so it's testable without the Workers runtime.
* The origin serving the rendering is the host of the request, which is `blaze-server.org` in production. An optional `ORIGIN` variable overrides it, so the Worker can be tested on another host, e.g. on a route of a test zone or its workers.dev host, against the real rendering. Redirects are absolute URLs on the origin.

Behaviour:

| Request | Answer |
| --- | --- |
| `GET`/`HEAD` of a path in the index, negotiated JSON or XML | the file, fetched from the origin and answered in place, with the origin's status and caching headers |
| `GET`/`HEAD` of a path in the index, negotiated HTML | `302` to the rendered page, e.g. `https://blaze-server.org/fhir/StructureDefinition-Job.html` |
| path not in the index | `404` without a request to the origin |
| any other method | `405` with `Allow: GET, HEAD` |

HTML is redirected rather than answered in place, because the rendered pages reference their stylesheets, the `tbl_*.png` sprites of the tree tables and their sibling pages relatively. Answered at `/fhir/StructureDefinition/Job`, those references resolve one directory too deep and break. Rewriting the page was rejected: a `<base href="/fhir/">` breaks the in-page anchors the pages use, and prefixing every relative URL misses the URLs the pages' scripts build and has to follow the publisher's output with every release. A browser shows the flat URL after the redirect, which is acceptable, because a reader cares about the page, not about the identifier.

Negotiation:

* `_format` wins over `Accept`, as in FHIR R4 (http.html#mime-type: it "allows a client to override the accept header value"). `json`, `application/json` and `application/fhir+json` mean JSON; `xml`, `text/xml`, `application/xml` and `application/fhir+xml` mean XML; `html` and `text/html` mean HTML. An unknown `_format` value is ignored, falling back to `Accept`.
* `Accept` is parsed with q-values and media type parameters (`application/fhir+json; fhirVersion=4.0`). The highest q wins. On equal q, the earlier entry wins. `text/html` and `application/xhtml+xml` mean HTML, the JSON and XML types as for `_format`, `*/*` and an absent header mean HTML.
* JSON is answered as `application/fhir+json`, XML as `application/fhir+xml`. GitHub Pages serves them as `application/json` and `application/xml`.
* Every negotiated answer, the redirect included, carries `Vary: Accept`, because GitHub Pages sends `Cache-Control: max-age=600` and a client that got one format must not get it back from a cache for a request asking for another.

### Location

A new module `modules/ig-resolver` holds the Worker, the index generator, their tests, the `wrangler` configuration and a `package.json` pinning `wrangler`. It's separate from `modules/ig`, so the deploy tooling doesn't join the SUSHI dependencies. TypeScript, tested with `node --test` like the chart code of the documentation.

`wrangler.jsonc` sets the Worker name, the entry point and `workers_dev: false`. It lists no routes, they come from the deploy.

### Build and Deploy

Everything runs in `docs.yml`, next to the deployment of the guide it resolves:

* `build-pages`, on every pull request and push:
  1. runs the tests of `modules/ig-resolver`,
  2. generates the index after the render and checks it against the rendering,
  3. uploads the index as an artifact.
* `deploy-ig-resolver`, only on `main`, after `deploy-pages`, so a route never points at a file that isn't live yet:
  1. checks out the repository, downloads the index,
  2. installs `wrangler` from the lockfile, in a step without the token, so the install scripts of its dependencies never see it,
  3. runs `wrangler deploy` with the routes derived from it.

  Installing and deploying are skipped while `CLOUDFLARE_ACCOUNT_ID` isn't configured, so the documentation deploys as before until the credentials exist. That's checked per step, because the variables of an environment aren't available yet in the `if` of a job.

The API token is the secret `CLOUDFLARE_API_TOKEN` and the account ID the variable `CLOUDFLARE_ACCOUNT_ID` of the `github-pages` environment, which is restricted to `main`. Pull requests never reach them. The token is account-owned, with the Workers role *Editor* at the Workers product scope and *Zone → Workers Routes → Write* restricted to the zone `blaze-server.org`. *Editor* can deploy existing Workers but not create new ones, so the role is *Admin* for the first deploy, which creates the Worker, and *Editor* afterwards. Tested on a separate account (2026-11-19): *Editor* fails to create the Worker, *Admin* creates it with its routes, *Editor* then deploys, and a deploy with fewer routes removes the dropped ones, also with the route permission restricted to the one zone.

### Failure Modes

* **Daily limit exceeded.** Routes should fail open, so requests bypass the Worker: canonicals return 404, the rest of the site is unaffected. Wrangler can't set this, so it's set on the routes in the dashboard.
* **Worker deploy fails.** The previous Worker and routes stay active. The guide itself is deployed before, so it's never stale because of the Worker.
* **Origin down.** The Worker passes the origin's error through.

## Decisions

* Worker, not Rules.
* Per-type routes, derived at deploy time.
* An exact index instead of a mechanical path rewrite, covering every resource of the guide.
* The issue promises canonicals only. The read URLs of the other resources come along because the implementation is the same.
* An unknown `_format` is ignored rather than answered with `415`.
* FHIR JSON and XML are answered in place, HTML is redirected to the rendered page.
* `wrangler` is pinned in the module's own `package.json` with a lockfile, so only the deploy job installs it, with its transitive dependencies pinned in the job that holds the token. Not a devDependency of the documentation, whose every build would install it, and not an unpinned `npx` call.
* The deploy is a separate job after the Pages deployment, whose install and deploy steps are skipped until `CLOUDFLARE_ACCOUNT_ID` is configured.
* An unencoded `+` in `_format`, which a query string decodes as a space, is read as `+`, so a hand-written `?_format=application/fhir+json` works.

## Open Points

* Whether fail open survives a deploy. Wrangler replaces the routes as a whole, so a fail mode set in the dashboard may be reset on every deploy. If it is, either the deploy sets it through the API after `wrangler deploy`, or the routes stay fail closed and a 1027 error on canonicals is accepted as the consequence of an exceeded limit.
