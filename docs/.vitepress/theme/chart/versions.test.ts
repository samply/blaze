import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  assertVersion,
  inVersionOrder,
  isRelease,
  select,
  sortVersions,
  versionLegend,
} from "./versions.ts";

describe("isRelease", () => {
  it("is true for release versions", () => {
    assert.equal(isRelease("1.11.0"), true);
    assert.equal(isRelease("0.34.0"), true);
  });

  it("is false for everything else", () => {
    assert.equal(isRelease("5773c58b"), false);
    assert.equal(isRelease("1.0.0-rc.1"), false);
    assert.equal(isRelease("v1.11.0"), false);
  });
});

describe("sortVersions", () => {
  it("sorts releases numerically and the commit run last", () => {
    assert.deepEqual(sortVersions(["1.9.0", "5773c58b", "1.10.0", "0.34.0"]), [
      "0.34.0",
      "1.9.0",
      "1.10.0",
      "5773c58b",
    ]);
  });

  it("keeps a lone commit run", () => {
    assert.deepEqual(sortVersions(["5773c58b"]), ["5773c58b"]);
  });

  it("throws on more than one commit run", () => {
    assert.throws(
      () => sortVersions(["1.11.0", "5773c58b", "abcdef12"]),
      /more than one commit run: 5773c58b, abcdef12/,
    );
  });
});

describe("select", () => {
  it("appends a version", () => {
    assert.deepEqual(select(["1.11.0"], "1.10.0"), ["1.11.0", "1.10.0"]);
  });

  it("drops the first selected version on a third selection", () => {
    assert.deepEqual(select(["1.11.0", "1.10.0"], "1.9.0"), [
      "1.10.0",
      "1.9.0",
    ]);
  });

  it("deselects a selected version", () => {
    assert.deepEqual(select(["1.11.0", "1.10.0"], "1.11.0"), ["1.10.0"]);
  });

  it("can't deselect the last selected version", () => {
    assert.deepEqual(select(["1.11.0"], "1.11.0"), ["1.11.0"]);
  });
});

describe("inVersionOrder", () => {
  it("orders the selection by the sorted versions", () => {
    assert.deepEqual(
      inVersionOrder(["5773c58b", "1.10.0"], ["1.9.0", "1.10.0", "5773c58b"]),
      ["1.10.0", "5773c58b"],
    );
  });
});

describe("assertVersion", () => {
  it("accepts matching values", () => {
    assert.doesNotThrow(() =>
      assertVersion("LEA47/1.11.0.csv", "1.11.0", ["1.11.0", "1.11.0"]),
    );
  });

  it("throws on a mismatching value", () => {
    assert.throws(
      () =>
        assertVersion("LEA47/1.11.0.csv", "1.11.0", ["1.11.0", "1.10.0"]),
      /LEA47\/1.11.0.csv holds results of 1.11.0, 1.10.0 instead of 1.11.0/,
    );
  });
});

describe("versionLegend", () => {
  it("dashes all versions but the newest", () => {
    assert.deepEqual(
      versionLegend(["1.13.0", "cf90eb09"]).map((entry) => [
        entry.name,
        entry.dashed ?? false,
      ]),
      [
        ["1.13.0", true],
        ["cf90eb09", false],
      ],
    );
  });

  it("draws no marker, which would cover the gap of the dash", () => {
    assert.deepEqual(
      versionLegend(["1.13.0", "cf90eb09"]).map((entry) => entry.marker),
      [false, false],
    );
  });
});
