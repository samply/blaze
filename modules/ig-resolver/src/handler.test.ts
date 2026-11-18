import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { buildIndex } from "./build-index.ts";
import { handle } from "./handler.ts";

const index = buildIndex("https://blaze-server.org/fhir", [
  {
    resourceType: "StructureDefinition",
    id: "Job",
    url: "https://blaze-server.org/fhir/StructureDefinition/Job",
  },
]);

const contentTypes: Record<string, string> = {
  html: "text/html; charset=utf-8",
  json: "application/json; charset=utf-8",
  xml: "application/xml",
};

/**
 * An origin answering every request with the file name it was asked for, the
 * way GitHub Pages types it, and recording the requests it got.
 */
function fakeOrigin() {
  const requests: Request[] = [];
  const fetchOrigin = async (request: Request) => {
    requests.push(request);
    const extension = new URL(request.url).pathname.split(".").pop() ?? "";
    return new Response(
      request.method === "HEAD" ? null : new URL(request.url).pathname,
      {
        status: 200,
        headers: {
          "Content-Type": contentTypes[extension] ?? "application/octet-stream",
          "Cache-Control": "max-age=600",
          ETag: '"abc"',
        },
      },
    );
  };
  return { requests, fetchOrigin };
}

const origin = "https://blaze-server.org";

function get(path: string, headers: Record<string, string> = {}) {
  return new Request(`https://blaze-server.org${path}`, { headers });
}

describe("handle", () => {
  describe("a known read path", () => {
    it("redirects to the HTML page by default", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      const response = await handle(get("/fhir/StructureDefinition/Job"), {
        index,
        origin,
        fetchOrigin,
      });

      assert.equal(response.status, 302);
      assert.equal(
        response.headers.get("Location"),
        "https://blaze-server.org/fhir/StructureDefinition-Job.html",
      );
      assert.equal(requests.length, 0);
    });

    it("redirects to the HTML page on _format=html, without the query", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        get("/fhir/StructureDefinition/Job?_format=html", {
          Accept: "application/fhir+json",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.status, 302);
      assert.equal(
        response.headers.get("Location"),
        "https://blaze-server.org/fhir/StructureDefinition-Job.html",
      );
    });

    it("redirects HEAD to the HTML page", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        new Request("https://blaze-server.org/fhir/StructureDefinition/Job", {
          method: "HEAD",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.status, 302);
      assert.equal(
        response.headers.get("Location"),
        "https://blaze-server.org/fhir/StructureDefinition-Job.html",
      );
    });

    it("answers FHIR JSON on Accept", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        get("/fhir/StructureDefinition/Job", {
          Accept: "application/fhir+json",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.status, 200);
      assert.equal(await response.text(), "/fhir/StructureDefinition-Job.json");
      assert.equal(
        response.headers.get("Content-Type"),
        "application/fhir+json;charset=utf-8",
      );
    });

    it("answers FHIR XML on Accept", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        get("/fhir/StructureDefinition/Job", {
          Accept: "application/fhir+xml",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(await response.text(), "/fhir/StructureDefinition-Job.xml");
      assert.equal(
        response.headers.get("Content-Type"),
        "application/fhir+xml;charset=utf-8",
      );
    });

    it("answers on _format over Accept", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        get("/fhir/StructureDefinition/Job?_format=json", {
          Accept: "text/html",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(await response.text(), "/fhir/StructureDefinition-Job.json");
    });

    it("answers on an unencoded _format=application/fhir+json", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        get("/fhir/StructureDefinition/Job?_format=application/fhir+json", {
          Accept: "text/html",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.status, 200);
      assert.equal(await response.text(), "/fhir/StructureDefinition-Job.json");
    });

    it("varies on Accept", async () => {
      for (const accept of [
        "text/html",
        "application/fhir+json",
        "application/fhir+xml",
      ]) {
        const { fetchOrigin } = fakeOrigin();

        const response = await handle(
          get("/fhir/StructureDefinition/Job", { Accept: accept }),
          { index, origin, fetchOrigin },
        );

        assert.equal(response.headers.get("Vary"), "Accept", accept);
      }
    });

    it("keeps the caching headers of the origin", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(
        get("/fhir/StructureDefinition/Job", {
          Accept: "application/fhir+json",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.headers.get("Cache-Control"), "max-age=600");
      assert.equal(response.headers.get("ETag"), '"abc"');
    });

    it("forwards conditional request headers to the origin", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      await handle(
        get("/fhir/StructureDefinition/Job", {
          Accept: "application/fhir+json",
          "If-None-Match": '"abc"',
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(requests[0]?.headers.get("If-None-Match"), '"abc"');
    });

    it("drops the query string on the request to the origin", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      await handle(get("/fhir/StructureDefinition/Job?_format=xml"), {
        index,
        origin,
        fetchOrigin,
      });

      assert.equal(
        requests[0]?.url,
        "https://blaze-server.org/fhir/StructureDefinition-Job.xml",
      );
    });

    it("answers HEAD with HEAD to the origin", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      const response = await handle(
        new Request("https://blaze-server.org/fhir/StructureDefinition/Job", {
          method: "HEAD",
          headers: { Accept: "application/fhir+json" },
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.status, 200);
      assert.equal(requests[0]?.method, "HEAD");
      assert.equal(
        response.headers.get("Content-Type"),
        "application/fhir+json;charset=utf-8",
      );
    });

    it("passes an error of the origin through", async () => {
      const response = await handle(
        get("/fhir/StructureDefinition/Job", {
          Accept: "application/fhir+json",
        }),
        {
          index,
          origin,
          fetchOrigin: async () =>
            new Response("Not Found", {
              status: 404,
              headers: { "Content-Type": "text/html" },
            }),
        },
      );

      assert.equal(response.status, 404);
      assert.equal(response.headers.get("Content-Type"), "text/html");
    });

    it("passes a 304 of the origin through", async () => {
      const response = await handle(
        get("/fhir/StructureDefinition/Job", {
          Accept: "application/fhir+json",
        }),
        {
          index,
          origin,
          fetchOrigin: async () =>
            new Response(null, { status: 304, headers: { ETag: '"abc"' } }),
        },
      );

      assert.equal(response.status, 304);
      assert.equal(response.headers.get("Vary"), "Accept");
    });
  });

  describe("an origin other than the host of the request", () => {
    const other = "https://example.org";
    const request = (accept: string) =>
      new Request("https://resolver.example.com/fhir/StructureDefinition/Job", {
        headers: { Accept: accept },
      });

    it("fetches the file from the origin", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      await handle(request("application/fhir+json"), {
        index,
        origin: other,
        fetchOrigin,
      });

      assert.equal(
        requests[0]?.url,
        "https://example.org/fhir/StructureDefinition-Job.json",
      );
    });

    it("redirects to the page at the origin", async () => {
      const { fetchOrigin } = fakeOrigin();

      const response = await handle(request("text/html"), {
        index,
        origin: other,
        fetchOrigin,
      });

      assert.equal(
        response.headers.get("Location"),
        "https://example.org/fhir/StructureDefinition-Job.html",
      );
    });
  });

  describe("an unknown path", () => {
    it("answers 404 without asking the origin", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      const response = await handle(get("/fhir/StructureDefinition/Unknown"), {
        index,
        origin,
        fetchOrigin,
      });

      assert.equal(response.status, 404);
      assert.equal(requests.length, 0);
    });
  });

  describe("another method", () => {
    it("answers 405 without asking the origin", async () => {
      const { fetchOrigin, requests } = fakeOrigin();

      const response = await handle(
        new Request("https://blaze-server.org/fhir/StructureDefinition/Job", {
          method: "POST",
        }),
        { index, origin, fetchOrigin },
      );

      assert.equal(response.status, 405);
      assert.equal(response.headers.get("Allow"), "GET, HEAD");
      assert.equal(requests.length, 0);
    });
  });
});
