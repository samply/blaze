import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { buildIndex, missingFiles, routes } from "./build-index.ts";

const canonical = "https://blaze-server.org/fhir";

describe("buildIndex", () => {
  it("takes the base path from the canonical", () => {
    assert.equal(buildIndex(canonical, []).basePath, "/fhir");
  });

  it("maps a canonical resource to its rendered files", () => {
    const index = buildIndex(canonical, [
      {
        resourceType: "StructureDefinition",
        id: "Job",
        url: "https://blaze-server.org/fhir/StructureDefinition/Job",
      },
    ]);

    assert.deepEqual(index.resources, {
      "StructureDefinition/Job": {
        html: "StructureDefinition-Job.html",
        json: "StructureDefinition-Job.json",
        xml: "StructureDefinition-Job.xml",
      },
    });
  });

  it("maps a resource without url like a canonical one", () => {
    const index = buildIndex(canonical, [
      { resourceType: "Task", id: "CompactJobExample" },
    ]);

    assert.deepEqual(index.resources["Task/CompactJobExample"], {
      html: "Task-CompactJobExample.html",
      json: "Task-CompactJobExample.json",
      xml: "Task-CompactJobExample.xml",
    });
  });

  it("maps the ImplementationGuide to the index page", () => {
    const index = buildIndex(canonical, [
      {
        resourceType: "ImplementationGuide",
        id: "blaze",
        url: "https://blaze-server.org/fhir/ImplementationGuide/blaze",
      },
    ]);

    assert.deepEqual(index.resources["ImplementationGuide/blaze"], {
      html: "index.html",
      json: "ImplementationGuide-blaze.json",
      xml: "ImplementationGuide-blaze.xml",
    });
  });

  describe("NamingSystem", () => {
    const namingSystem = (value: string, type = "uri") => ({
      resourceType: "NamingSystem",
      id: "JobNumberNamingSystem",
      uniqueId: [{ type, value }],
    });

    const files = {
      html: "NamingSystem-JobNumberNamingSystem.html",
      json: "NamingSystem-JobNumberNamingSystem.json",
      xml: "NamingSystem-JobNumberNamingSystem.xml",
    };

    it("maps its URI on the canonical to its rendered files", () => {
      const index = buildIndex(canonical, [
        namingSystem("https://blaze-server.org/fhir/sid/JobNumber"),
      ]);

      assert.deepEqual(index.resources, {
        "NamingSystem/JobNumberNamingSystem": files,
        "sid/JobNumber": files,
      });
    });

    it("doesn't map a URI outside the canonical", () => {
      const index = buildIndex(canonical, [
        namingSystem("https://example.com/fhir/sid/JobNumber"),
      ]);

      assert.deepEqual(Object.keys(index.resources), [
        "NamingSystem/JobNumberNamingSystem",
      ]);
    });

    it("doesn't map a unique id other than a URI", () => {
      const index = buildIndex(canonical, [
        namingSystem("https://blaze-server.org/fhir/sid/JobNumber", "other"),
      ]);

      assert.deepEqual(Object.keys(index.resources), [
        "NamingSystem/JobNumberNamingSystem",
      ]);
    });

    it("fails on a URI that is the read path of another resource", () => {
      assert.throws(
        () =>
          buildIndex(canonical, [
            { resourceType: "Task", id: "A" },
            namingSystem("https://blaze-server.org/fhir/Task/A"),
          ]),
        { message: "Task/A occurs more than once." },
      );
    });
  });

  it("fails on a url that isn't the read URL of its resource", () => {
    assert.throws(
      () =>
        buildIndex(canonical, [
          {
            resourceType: "CodeSystem",
            id: "JobType",
            url: "https://blaze-server.org/fhir/CodeSystem/job-type",
          },
        ]),
      {
        message:
          "The url https://blaze-server.org/fhir/CodeSystem/job-type of CodeSystem/JobType isn't https://blaze-server.org/fhir/CodeSystem/JobType.",
      },
    );
  });

  it("fails on two resources with the same type and id", () => {
    assert.throws(
      () =>
        buildIndex(canonical, [
          { resourceType: "Task", id: "A" },
          { resourceType: "Task", id: "A" },
        ]),
      { message: "Task/A occurs more than once." },
    );
  });
});

describe("missingFiles", () => {
  const index = buildIndex(canonical, [
    { resourceType: "Task", id: "A" },
    { resourceType: "Task", id: "B" },
  ]);

  it("is empty if every file exists", () => {
    assert.deepEqual(
      missingFiles(index, () => true),
      [],
    );
  });

  it("lists the files that don't exist", () => {
    const existing = new Set([
      "Task-A.html",
      "Task-A.json",
      "Task-A.xml",
      "Task-B.html",
    ]);

    assert.deepEqual(
      missingFiles(index, (file) => existing.has(file)),
      ["Task-B.json", "Task-B.xml"],
    );
  });
});

describe("routes", () => {
  it("is one route per resource type, sorted", () => {
    const index = buildIndex(canonical, [
      { resourceType: "ValueSet", id: "A" },
      { resourceType: "CodeSystem", id: "A" },
      { resourceType: "CodeSystem", id: "B" },
    ]);

    assert.deepEqual(routes(canonical, index), [
      "blaze-server.org/fhir/CodeSystem/*",
      "blaze-server.org/fhir/ValueSet/*",
    ]);
  });

  it("covers the URI of a NamingSystem", () => {
    const index = buildIndex(canonical, [
      {
        resourceType: "NamingSystem",
        id: "JobNumberNamingSystem",
        uniqueId: [
          { type: "uri", value: "https://blaze-server.org/fhir/sid/JobNumber" },
        ],
      },
    ]);

    assert.deepEqual(routes(canonical, index), [
      "blaze-server.org/fhir/NamingSystem/*",
      "blaze-server.org/fhir/sid/*",
    ]);
  });
});
