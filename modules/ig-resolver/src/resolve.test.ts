import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { buildIndex } from "./build-index.ts";
import { resolve } from "./resolve.ts";

const index = buildIndex("https://blaze-server.org/fhir", [
  { resourceType: "StructureDefinition", id: "Job" },
]);

describe("resolve", () => {
  it("finds the files of a read path", () => {
    assert.deepEqual(resolve(index, "/fhir/StructureDefinition/Job"), {
      html: "StructureDefinition-Job.html",
      json: "StructureDefinition-Job.json",
      xml: "StructureDefinition-Job.xml",
    });
  });

  it("finds nothing for an unknown id", () => {
    assert.equal(
      resolve(index, "/fhir/StructureDefinition/Unknown"),
      undefined,
    );
  });

  it("finds nothing outside of the base path", () => {
    assert.equal(resolve(index, "/StructureDefinition/Job"), undefined);
    assert.equal(resolve(index, "/fhirStructureDefinition/Job"), undefined);
  });

  it("finds nothing for a path with a trailing slash", () => {
    assert.equal(resolve(index, "/fhir/StructureDefinition/Job/"), undefined);
  });

  it("finds nothing for a deeper path", () => {
    assert.equal(
      resolve(index, "/fhir/StructureDefinition/Job/_history/1"),
      undefined,
    );
  });

  it("is case-sensitive", () => {
    assert.equal(resolve(index, "/fhir/structuredefinition/job"), undefined);
  });

  it("finds nothing for keys of the object prototype", () => {
    assert.equal(resolve(index, "/fhir/constructor"), undefined);
    assert.equal(resolve(index, "/fhir/__proto__"), undefined);
  });
});
