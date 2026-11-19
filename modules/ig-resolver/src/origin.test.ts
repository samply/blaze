import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { originOf } from "./origin.ts";

describe("originOf", () => {
  const request = new Request(
    "https://blaze-server.org/fhir/CodeSystem/JobType",
  );

  it("is the origin of the request without a configured origin", () => {
    assert.equal(originOf(request, {}), "https://blaze-server.org");
  });

  it("is the configured origin", () => {
    assert.equal(
      originOf(request, { ORIGIN: "https://example.org" }),
      "https://example.org",
    );
  });

  it("is the origin of the configured URL, without its path", () => {
    assert.equal(
      originOf(request, { ORIGIN: "https://example.org/some/path" }),
      "https://example.org",
    );
  });

  it("ignores an empty configured origin", () => {
    assert.equal(originOf(request, { ORIGIN: "" }), "https://blaze-server.org");
  });
});
