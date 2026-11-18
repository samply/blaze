import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { negotiate } from "./negotiate.ts";

describe("negotiate", () => {
  describe("without Accept and _format", () => {
    it("is html", () => {
      assert.equal(negotiate(null, null), "html");
    });
  });

  describe("on Accept", () => {
    it("recognises the JSON media types", () => {
      assert.equal(negotiate("application/fhir+json", null), "json");
      assert.equal(negotiate("application/json", null), "json");
    });

    it("recognises the XML media types", () => {
      assert.equal(negotiate("application/fhir+xml", null), "xml");
      assert.equal(negotiate("application/xml", null), "xml");
      assert.equal(negotiate("text/xml", null), "xml");
    });

    it("recognises the HTML media types", () => {
      assert.equal(negotiate("text/html", null), "html");
      assert.equal(negotiate("application/xhtml+xml", null), "html");
    });

    it("is html for */*", () => {
      assert.equal(negotiate("*/*", null), "html");
    });

    it("is html for unknown media types only", () => {
      assert.equal(negotiate("image/png", null), "html");
    });

    it("ignores media type parameters", () => {
      assert.equal(
        negotiate("application/fhir+json; fhirVersion=4.0", null),
        "json",
      );
    });

    it("ignores case and whitespace", () => {
      assert.equal(negotiate("  Application/FHIR+JSON  ", null), "json");
    });

    it("prefers the highest q-value", () => {
      assert.equal(
        negotiate("application/fhir+xml;q=0.5, application/fhir+json", null),
        "json",
      );
    });

    it("prefers the earlier entry on equal q-values", () => {
      assert.equal(
        negotiate("application/fhir+xml, application/fhir+json", null),
        "xml",
      );
    });

    it("excludes media types with q=0", () => {
      assert.equal(
        negotiate(
          "application/fhir+json;q=0, application/fhir+xml;q=0.1",
          null,
        ),
        "xml",
      );
    });

    it("is html for the Accept header of a browser", () => {
      assert.equal(
        negotiate(
          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          null,
        ),
        "html",
      );
    });

    it("skips unknown media types before a known one", () => {
      assert.equal(negotiate("image/png, application/fhir+json", null), "json");
    });
  });

  describe("on _format", () => {
    it("recognises the JSON values", () => {
      for (const format of [
        "json",
        "application/json",
        "application/fhir+json",
      ]) {
        assert.equal(negotiate(null, format), "json", format);
      }
    });

    it("recognises the XML values", () => {
      for (const format of [
        "xml",
        "text/xml",
        "application/xml",
        "application/fhir+xml",
      ]) {
        assert.equal(negotiate(null, format), "xml", format);
      }
    });

    it("recognises the HTML values", () => {
      assert.equal(negotiate("application/fhir+json", "html"), "html");
      assert.equal(negotiate("application/fhir+json", "text/html"), "html");
    });

    it("wins over Accept", () => {
      assert.equal(negotiate("text/html", "json"), "json");
      assert.equal(negotiate("application/fhir+json", "xml"), "xml");
    });

    it("ignores media type parameters", () => {
      assert.equal(
        negotiate(null, "application/fhir+json;fhirVersion=4.0"),
        "json",
      );
    });

    it("reads a space as the + of an unencoded media type", () => {
      assert.equal(negotiate(null, "application/fhir json"), "json");
      assert.equal(negotiate(null, "application/fhir xml"), "xml");
    });

    it("falls back to Accept on an unknown value", () => {
      assert.equal(negotiate("application/fhir+json", "turtle"), "json");
    });

    it("falls back to Accept on an empty value", () => {
      assert.equal(negotiate("application/fhir+xml", ""), "xml");
    });
  });
});
