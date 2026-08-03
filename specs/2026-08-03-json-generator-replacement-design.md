# Replacing Jackson's JsonGenerator with a Blaze-owned generator

**Date:** 2026-08-03
**Status:** Plan, not yet implemented
**Scope:** `modules/fhir-structure` (write path only)
**Builds on:** [2026-08-02-fhir-map-classes-design.md](2026-08-02-fhir-map-classes-design.md)
(implemented on this branch)

This document is self-contained. It records the inventory of what Blaze
actually uses of Jackson's write API, the design of the replacement, the
compatibility constraints, and the build order.

## Summary

Replace `com.fasterxml.jackson.core.JsonGenerator` on the **write path** with a
Blaze-owned generator abstraction — one abstract class with the small, closed
method set FHIR serialization actually needs, and two concrete
implementations, UTF-8 JSON and CBOR. The generator does **no structural validation**: Blaze's serializers are
correct by construction, so Jackson's `JsonWriteContext` state machine, its
`_verifyValueWrite` checks and its feature-flag branches are pure overhead.

The new generator additionally supports writing a **field name in two parts** —
prefix and type suffix — which dissolves the per-property type→field-name
tables: `PolymorphicPropertyHandler` no longer carries a `Keyword[]`/
`FieldName[]` pair per polymorphic property, and the ~40 per-class
`FIELD_NAME_EXTENSION_VALUE` constants disappear. Each FHIR type instead knows
its own suffix (`Boolean`, `Quantity`, `DateTime`, …) as one shared constant,
and the field name `valueQuantity` is emitted as `value` + `Quantity` at write
time, at the cost of one extra `arraycopy`.

The same move deletes `FieldName` itself. Its only purpose is to pair each
name with its `_`-prefixed sibling (`birthDate`/`_birthDate`) for extended
primitives; a generator that owns the byte stream can emit the `_` itself, via
`writeExtendedFieldName`. Every property then holds exactly one precomputed
name, and the `_name` variants — today materialized for every primitive
property although extended primitives are rare — are never built at all.

**Parsing stays on Jackson.** `JsonParser`/`CBORParser`, the
`StreamReadConstraints` and the whole of `resource.clj`'s read side are
untouched, so the `jackson-core` and `jackson-dataformat-cbor` dependencies
remain.

**Jackson stays as the test oracle.** A thin adapter implements the new
abstraction on top of Jackson's generator, so the same serialization code can
run through both and be compared byte-for-byte — as **permanent**
property-based differential tests, not a migration-time harness (see "The
Jackson adapter" and "Risk assessment").

## Problem

### What Jackson costs on every write

`UTF8JsonGenerator` maintains a `JsonWriteContext` — a linked chain of parent
contexts with an index, a state code and a current-name slot. Every
`writeFieldName` runs `_writeContext.writeFieldName(name)` and switches on the
returned status; every value write runs `_verifyValueWrite`, which switches on
`_writeContext.writeValue()` and re-checks that a field name was written where
one is expected. None of this can ever fire in Blaze: the serializers are
generated from element definitions and pair `writeStartObject`/`writeEndObject`
and name/value strictly by construction. The checks exist because Jackson
cannot trust arbitrary callers; Blaze can trust its own.

On top of the state machine come `SerializableString` indirections
(`writeFieldName(SerializableString)` → `_writeFieldName` → quoting-cache
lookup → copy), cfg-feature branches (escaping mode, duplicate detection,
root-value separators) and, on `close()`, auto-close bookkeeping.

Current write benchmark (KDS bundle, 416 KB, from the `comment` block in
`modules/fhir-structure/test-perf/blaze/fhir/spec_test_perf.clj`, measured
2026-08-02 on this branch): **~233 µs**. Every FHIR read request pays this once
(JSON to the response) and every write request twice more (CBOR to the resource
store, hashing aside).

### What the polymorphic tables cost

`Observation.value[x]` serializes as `valueQuantity`, `valueBoolean`, … — the
field name depends on the runtime type. Today
[PolymorphicPropertyHandler.java](../modules/fhir-structure/java/blaze/fhir/writing/PolymorphicPropertyHandler.java)
solves this with two parallel arrays per polymorphic property — all possible
type keywords and all possible pre-built `FieldName`s — and an identity scan at
write time. The open types (`ElementDefinition.defaultValue[x]`, `fixed[x]`,
`pattern[x]`, `Extension.value[x]`, `Parameters.parameter.value[x]`, …) each
carry ~50-entry tables. `Extension.value[x]` is special-cased a second way:
every one of the ~40 Java type classes holds a `FIELD_NAME_EXTENSION_VALUE`
constant (`valueBoolean`/`_valueBoolean` etc.) behind the
`ExtensionValue.fieldNameExtensionValue()` method — the same information,
duplicated per class instead of per property.

Both exist only because Jackson's `writeFieldName` needs the *complete* name as
one `SerializableString`. A generator that can write `value` + `Quantity` as
two parts needs neither: the prefix belongs to the property, the suffix to the
type, and the value dispatches on itself.

## Considered, deferred to v2: raw bytes for `Base64Binary`

`Base64Binary.value` is a `java.lang.String` holding base64 text, written with
plain `writeString` and parsed verbatim. That representation is an oversight —
but fixing it is **deferred to Blaze v2** (decided 2026-08-04), where binary
storage moves to its own RocksDB column family behind a complete database
migration anyway. The analysis below is kept as input to that design.

Why deferred, in order of weight:

1. **v1 has never broken downgrade capability over a storage format.** That
   is a product guarantee users rely on implicitly, and it is worth more
   than a 25% saving on one datatype. The two-release rollout below would
   preserve *one-step* downgrade but still retire the unconditional
   guarantee — spent on an intermediate format that v2 immediately
   obsoletes.
2. **v2's own column family is strictly better than CBOR byte strings.**
   Byte strings still leave megabyte blobs inline in the resource CBOR —
   parsed into the heap, weighed into the resource cache, copied through
   every read. A dedicated column family takes blobs out of the resource
   entirely: no parse cost, no cache bloat, streaming serve straight from
   storage, and the in-memory representation can become a handle rather
   than an inline `byte[]` — none of which the intermediate step delivers.
3. **Without the storage flip, the v1-only remainder is a bad trade.**
   Raw bytes in memory with base64 text still in CBOR means decoding on
   every parse and encoding on every store write and every JSON serve —
   codec work on the common JSON-API workload where today a string passes
   through untouched — to buy a memory win that v2's design supersedes.

Consequences for **this** plan: `Base64Binary` stays a `String` in v1, so
`Binary.data`/`Attachment.data` remain the largest `writeString` values, the
chunked-`writeString` requirement and the CBOR long-string risk item below
apply in full, and `writeBase64Binary` joins the generator interface only in
v2. What carries into the v2 design from the analysis below: the
`writeBinary` split mechanics, the dual-token parse (needed at most during
migration — a full migration rewrites every entry, so no mixed-format window
exists in operation), the hash-preservation technique, and the accepted
normalization decision, which then applies at migration time as well.

**The codec already runs — at the wrong ends.** Blaze pays base64 conversions
today precisely where raw bytes would make them free:

- serving a `Binary` resource with its native content type decodes the
  `String` on every request
  ([output.clj:60](../modules/rest-util/src/blaze/middleware/fhir/output.clj:60));
- accepting a native binary upload *encodes* the body into a `String`
  ([resource.clj:136](../modules/rest-util/src/blaze/middleware/fhir/resource.clj:136));
- every `$evaluate-measure` decodes the CQL source out of `Library.content`
  ([measure.clj:64](../modules/operation-measure-evaluate-measure/src/blaze/fhir/operation/evaluate_measure/measure.clj:64)).

Meanwhile the store path, which needs no base64 at all, carries it
everywhere: base64 text in memory (4/3 × the octets, on the largest values in
the resource cache), base64 text in the stored CBOR (4/3 × the octets on
disk), and string copies through every parse and write.

**With raw bytes** the picture inverts. The native-binary path becomes
codec-free end to end (body → `byte[]` → CBOR byte string → `byte[]` → body).
Memory and stored size drop 25% on exactly the biggest values. Base64 is
produced only where the wire format demands it — the JSON (and XML)
representation — by a new generator method:

- `writeBase64Binary(byte[])` — JSON: streaming base64-encode into the
  buffer; the output is pure ASCII, needs no escaping, and chunks naturally
  (3 input bytes → 4 output chars). CBOR: a definite-length **byte string**
  (major type 2) — the length is `bytes.length`, known upfront, so the
  long-string chunking problem does not exist on this path.
- The adapter maps it to Jackson's `writeBinary`, which does exactly this
  split (MIME-no-linefeeds base64 in JSON, byte string in CBOR) — the
  differential oracle carries over unchanged.

In v2 this removes the largest values from `writeString` entirely: the
chunked-`writeString` requirement keeps `Xhtml` narratives as its largest
case, and the CBOR long-string item loses its megabyte driver. In v1 — this
plan — both apply in full, as stated under "Consequences" above.

**Four compatibility gates**, none fatal — recorded as they were analyzed
for a v1 change; in v2 the migration collapses gate 3 (every entry is
rewritten, so no mixed-format operation and no two-release dance), while
gates 1, 2 and 4 apply unchanged:

1. **Content hash.** Today the hash commits to the base64 *text*
   (`Strings.hashInto` → marker + UTF-8 bytes, which for base64 are the
   ASCII chars). Preserve it by streaming-encoding the raw bytes into the
   `PrimitiveSink` in chunks — byte-identical hash stream, no allocation.
   Same harness-first discipline as the map-classes change.
2. **Non-canonical input — normalization accepted (decided 2026-08-03).**
   Decode → re-encode canonicalizes the base64, changing hash and JSON output
   for non-canonical input; this divergence is accepted, the mirror of the
   map-design's explicit-nil decision. The exposure is narrower than FHIR's
   spec suggests: Blaze already validates `base64Binary` against
   `([0-9a-zA-Z+/=]{4})+`
   ([resource.clj:912](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:912)),
   which — unlike FHIR's official regex — permits **no whitespace**, and
   `use-regex` is on by default in the parsing context. So whitespace never
   entered through validating paths; what remains is (a) non-zero trailing
   padding bits (`QR==` normalizes to `QQ==`) and (b) strings that match the
   regex but are not decodable base64 (`=AAA`, `====`) — today stored
   verbatim, after the change rejected at parse time. Both are defects in the
   input, and (b) turning into a parse error is validation working, not a
   regression.
3. **Stored CBOR.** Byte strings are a storage-format change: the parser must
   accept both (old text entries keep decoding on read), but entries written
   in the new format are unreadable by Blaze versions that predate the
   dual-token parser. Writing base64 text into CBOR "for compatibility"
   forever instead would *add* codec work to the store path to avoid the
   format change; not worth it — but the rollout needs care:

   **What an old version does with a byte-string entry** — precisely: the
   `base64Binary` handler matches only `JsonToken/VALUE_STRING`, so
   `VALUE_EMBEDDED_OBJECT` falls through to `incorrect-value-anom`
   ([resource.clj:613](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:613)),
   and the resource store wraps it with the resource hash
   ([kv.clj:47-50](../modules/db-resource-store/src/blaze/db/resource_store/kv.clj:47)).
   Every read that pulls that resource fails with the anomaly — not only
   `Binary` reads, but any search bundle, history page, batch or CQL
   evaluation that touches it. The stored bytes are intact and re-upgrading
   restores full readability; it is unavailability, not data loss.

   **Downgrade is not the only exposure.** In distributed deployments the
   Cassandra resource store is shared by several Blaze nodes, so during a
   *rolling upgrade* an old node can read an entry a new node just wrote —
   the same failure during normal operations, no rollback involved.

   **Therefore: two-release rollout.** Release *N* ships the dual-token
   parser (and may ship the whole in-memory `byte[]` change) but still
   writes base64 text into CBOR; release *N+1* flips the writer to byte
   strings. Rolling upgrades *N* → *N+1* are then safe in both directions,
   downgrade from *N+1* to *N* is safe, and only skipping *N* entirely is
   exposed — which the release notes state explicitly (minimum version for
   downgrade / mixed-cluster operation is *N*). The cost is that the storage
   and store-path CPU wins arrive one release later; the in-memory 25% and
   the codec-free native-binary serve path can land already in *N*, at the
   transitional cost of one encode per store write during that release.

   The mechanics are clean on both sides, because this change lands while
   Jackson still drives both paths. **Writing:** `Base64Binary` calls
   Jackson's `writeBinary(byte[])`, which does the right thing per format by
   itself — the JSON generator emits a quoted base64 string using the
   default `MIME_NO_LINEFEEDS` variant (standard alphabet, `=` padding, no
   line breaks — byte-identical to the `java.util.Base64.getEncoder()`
   output Blaze produces today), while `CBORGenerator` overrides it to emit
   a definite-length byte string (major type 2, length known from the
   array). **Reading:** the two forms arrive as *different tokens*, so the
   parser branches naturally. A text string — JSON input and all old stored
   CBOR — is `JsonToken.VALUE_STRING`, exactly what the `base64Binary`
   handler matches today
   ([resource.clj:910-912](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:910));
   a CBOR byte string is `JsonToken.VALUE_EMBEDDED_OBJECT`, whose bytes
   `parser.getBinaryValue()` returns directly. The handler accepts both
   tokens: `VALUE_STRING` → decode (+ validate), `VALUE_EMBEDDED_OBJECT` →
   take the array as-is. The JSON parser never produces
   `VALUE_EMBEDDED_OBJECT`, so one handler serves both formats — and
   `getBinaryValue()` also works on `VALUE_STRING`, decoding out of
   Jackson's internal buffer without materializing the base64 `String`
   first. When the custom generator later replaces Jackson on the write
   path, `writeBase64Binary` takes over exactly this split and the adapter
   maps it back to Jackson's `writeBinary`, keeping the differential oracle.
4. **API surface.** `(:value b64)` returns the base64 `String` today. Keep
   that contract — `value()` encodes on demand — and add a raw accessor;
   the three call sites above move to the raw accessor as part of the change.
   `equals`/`hasheq`/`memSize` move to the byte array (`Arrays`-based, not
   identity).

## Inventory: the API surface actually used

Counted over `modules/fhir-structure/java` on 2026-08-03:

| Method | Call sites | Notes |
| --- | --- | --- |
| `writeStartObject()` / `writeEndObject()` | 41 / 41 | always paired, never with size hint |
| `writeFieldName(SerializableString)` | 35 | always precomputed, never a plain `String` |
| `writeString(String)` | 34 | needs full JSON escaping |
| `writeNull()` | 24 | extended-primitive list placeholders |
| `writeNumber(int \| long \| BigDecimal)` | 10 | `Integer`/`UnsignedInt`/`PositiveInt` int, `Integer64` long, `Decimal` BigDecimal |
| `writeStartArray()` / `writeEndArray()` | 4 / 4 | only in the three list helpers |
| `writeBoolean(boolean)` | 2 | |
| `writeRawUTF8String(byte[], int, int)` | 1 | `DateTimeBuffer` — pure ASCII, no escaping needed |

Nothing else: no `writeObjectField*`, no plain-`String` field names, no raw
output, no codec/`ObjectMapper` use on the write path. 78 Java files import
`com.fasterxml.jackson.core.JsonGenerator`, 8 import `SerializableString`, 15
import `com.fasterxml.jackson.core.io` (`SerializedString`) — the migration is
almost entirely an import swap because the method names can stay identical.

Both backends flow through the same call sites:
[resource.clj:1264-1299](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:1264)
creates a generator from either `json-factory` or `cbor-factory` and calls
`Complex.serializeAsJsonValue` on it. So the replacement must be an
*abstraction* with a JSON and a CBOR implementation, exactly as Jackson is —
the call sites stay bimorphic either way, which HotSpot inlines.

One Jackson write-side use survives unchanged:
[hash.clj:66](../modules/fhir-structure/src/blaze/fhir/hash.clj:66) registers a
databind `JsonSerializer` for `Hash`, used by jsonista object mappers in
`admin-api` and friends. That is a different pipeline (databind over non-FHIR
maps) and keeps Jackson.

## Design

### The classes

Package `blaze.fhir.writing`, alongside the property handlers:

- **`JsonGenerator`** — abstract base, `Closeable`. Declares exactly the
  inventory above plus five additions: `writeExtendedFieldName(EncodedName)`
  (the `_name` form, underscore emitted by the generator), the two-part
  `writeFieldName(EncodedName prefix, EncodedName suffix)` and
  `writeExtendedFieldName(EncodedName prefix, EncodedName suffix)` (see
  "Two-part field names"), `writeElementSeparator()` (see "Separator
  handling"), and a `writeString(EncodedName)` overload for the pre-encoded
  `resourceType` value (see "`EncodedName`"). Keeping the
  name `JsonGenerator` is deliberate: FHIR's CBOR representation *is* the JSON
  model in CBOR encoding (Jackson's own `CBORGenerator extends JsonGenerator`
  has the same shape), and it turns the 78-file migration into a one-line import
  swap per file.
- **`Utf8JsonGenerator extends JsonGenerator`** — final. Writes UTF-8 bytes
  into an internal `byte[]` buffer (8 KiB initial), flushing to the target
  `OutputStream`. No `JsonWriteContext`, no feature flags, no
  `SerializableString`.
- **`CborGenerator extends JsonGenerator`** — final. Same buffer scheme,
  emitting exactly the byte encodings Jackson's `CBORGenerator` produces today
  (see "CBOR compatibility").

`resource.clj`'s `write-value` loses the factory indirection:

```clojure
(with-open [gen (Utf8JsonGenerator/create out)]   ; or CborGenerator/create
  (.serializeAsJsonValue ^Complex value gen))
```

The public API (`fhir-spec/write-json`, `write-json-as-bytes`,
`write-json-as-string`, `write-cbor`) is unchanged, so no module outside
`fhir-structure` changes at all.

### `EncodedName` — replacing `SerializedString`

A precomputed name, interned like `FieldName` is today:

```java
public final class EncodedName {
    final byte[] utf8;        // raw name bytes            — CBOR + two-part parts
    final byte[] jsonQuoted;  // '"' + name + '"' + ':'    — one arraycopy in JSON
    final byte[] cbor;        // length header + utf8      — one arraycopy in CBOR
}
```

FHIR element names are ASCII without escapable characters — assert that at
construction, so name writing never runs the escaper.

**The arrays are never returned, so they are never copied.** A defensive copy
per accessor call would put an allocation on every field-name write — worse
than the Jackson indirection this design removes — and returning the arrays
directly from a public accessor would let callers mutate an interned,
thread-shared object. The resolution is the same one the `*Map` classes use
for their `values` array: no accessor exists. `EncodedName` and both
generators live in `blaze.fhir.writing`, the fields are package-private
`final`, and the generators `arraycopy` straight out of them; nothing outside
the package can reach the arrays. Interned instances are published via final
fields, so sharing across threads is safe. This is also why adapter rule 1
(below) and the `EncodedName` unit tests work the way they do: the adapter
uses `toString()`, and the tests verify the precomputed variants
*behaviorally* — write a name through a generator and compare the output bytes
against `SerializedString.asQuotedUTF8()` / a Jackson-written name — rather
than through an accessor that would exist only for tests. (The cold-path
convention differs deliberately: `TypeMetadata.keys()` clones, because
registry introspection can afford it. Hot paths don't get accessors at all.)

**What the `arraycopy` actually is.** "No copy" above means no *allocation*
and no *escape* — bytes still move, exactly as they do in Jackson. The
pipeline has two stages:

1. Every write method copies its bytes into the generator's internal buffer
   with `System.arraycopy(name.jsonQuoted, 0, buffer, pos, len)` — a JIT
   intrinsic (vectorized memmove between two existing arrays), no allocation,
   no new array. `EncodedName`'s array is only ever the *source* of such a
   copy; nothing writes into it and nothing retains it.
2. The `OutputStream` sees only the generator's **own** buffer, in batches:
   `out.write(buffer, 0, count)` when the buffer fills and on `flush`/
   `close` — once per 8 KiB, not once per token. Handing the private buffer
   to `write` is safe by the `OutputStream` contract (the callee reads it
   synchronously and must not retain it); `EncodedName`'s arrays are never
   passed to it.

Writing each name's array straight to the stream instead would trade one
intrinsic copy of ~5–20 bytes for a virtual `write` call per token — and
fragment the stream into single-byte writes for the interstitial `"`, `:`,
`,` characters. Batching through an owned buffer is why Jackson has the same
two-stage shape.

Values, however, are not all name-sized: `Binary.data` and `Attachment.data`
are `Base64Binary`, whose value is a plain Java `String` that can run to
megabytes, written through `writeString` like any other primitive. So
**`writeString` must stream in chunks from the first pass** — encode into the
buffer, drain to the `OutputStream` when it fills, continue — and must never
allocate a value-sized temporary (`String.getBytes(UTF_8)` on a 10 MB base64
value would allocate 10 MB; encode from the `String` directly, the way Jackson
does). This is a correctness-of-design requirement, not a later optimization;
`Xhtml` narratives are the second-largest case of the same thing. (In Blaze
v2, `Base64Binary` moves to raw bytes and its own storage — see "Considered,
deferred to v2" — and the megabyte case leaves `writeString`; in v1 it is
first-class here.)

[FieldName](../modules/fhir-structure/java/blaze/fhir/spec/type/FieldName.java)
is **deleted**, not reshaped. It exists only to pair `name` with `_name` for
extended primitives; instead, `writeExtendedFieldName(EncodedName)` writes the
underscore itself — JSON emits `"` `_` + `utf8` + `":` (two constant bytes
around one `arraycopy`), CBOR emits a length header of `utf8.length + 1`
followed by `_` and the bytes. Output is byte-identical to the precomputed
`_name`; the cost is two byte writes on a path only extended primitives ever
take. Everything that holds a `FieldName` today — `PrimitivePropertyHandler`,
`StringPropertyHandler`, the primitive types' `serializeJsonField(generator,
fieldName)` signatures, `Primitive.serializeJsonPrimitiveList` — holds a
single `EncodedName` instead, and `EncodedName` takes over the interner.

`TypeMetadata.resourceType()` and `ResourceMap`'s `resourceType` value become an
`EncodedName`-like pre-encoded *string value* (`"Patient"` with quotes for
JSON, header + bytes for CBOR), written by a `writeString(EncodedName)`
overload — the one string value that is written on every resource and never
needs escaping.

### Two-part field names and polymorphic dispatch

New method on the generator:

```java
// writes `"` + prefix + suffix + `":` (JSON)
// or header(len₁+len₂) + prefix + suffix (CBOR)
void writeFieldName(EncodedName prefix, EncodedName suffix);

// the extended sibling: `"_` + prefix + suffix + `":` (JSON)
// or header(1+len₁+len₂) + `_` + prefix + suffix (CBOR)
void writeExtendedFieldName(EncodedName prefix, EncodedName suffix);
```

JSON writes it as two `arraycopy`s by precomputing the prefix as `'"' + name`
and the suffix as `name + '"' + ':'` (variant arrays on `EncodedName`, built at
intern time). CBOR computes the combined length header at write time — a few
arithmetic ops — then copies both `utf8` arrays. In both encodings the bytes
are **identical** to writing the concatenated name in one call.

Each FHIR type gains one shared suffix constant, exposed as a method on
`ExtensionValue` (replacing `fieldNameExtensionValue`):

```java
public interface ExtensionValue extends Element {
    EncodedName typeSuffix();   // "Boolean", "Quantity", "DateTime", …
}
```

The suffix is the capitalized type code — exactly what
[type_metadata.clj:68](../modules/fhir-structure/src/blaze/fhir/type_metadata.clj:68)
concatenates today, minus the prefix. For the four `*Map` classes the suffix
comes from `TypeMetadata` (the type name), for robustness; in R4 every type
that can actually appear in a choice element (`value[x]`, `defaultValue[x]`,
…) has a Java implementation, so the map path should be unreachable — verify
this against the element definitions during implementation and keep the
metadata fallback either way.

Dispatch inverts: instead of the handler scanning a table for the value's
type, the value serializes itself under a prefix:

```java
// ExtensionValue — complex default
default void serializeJsonPolymorphicField(JsonGenerator generator, EncodedName prefix) {
    generator.writeFieldName(prefix, typeSuffix());
    serializeAsJsonValue(generator);
}

// primitive types override, mirroring today's serializeJsonField(generator, fieldName):
if (hasValue())   { generator.writeFieldName(prefix, typeSuffix());         …value… }
if (isExtended()) { generator.writeExtendedFieldName(prefix, typeSuffix()); …ext…  }
```

`writeExtendedFieldName(prefix, suffix)` composes `_` + `value` + `Boolean` to
`_valueBoolean` at write time, so extended polymorphic primitives cost nothing
extra and need no precomputed variant either. Choice elements are always
single-valued in FHIR, so there is no list case.

What shrinks:

- `PolymorphicPropertyHandler` becomes `(Keyword key, EncodedName prefix)` —
  the `Keyword[] types` and `FieldName[] fieldNames` arrays, the
  `ILookup.valAt(:fhir/type)` call and the identity scan all go.
- `FieldName` and its interner are deleted; the `_name` `SerializedString`s —
  one per primitive property today, almost all never written — are never
  built.
- `ExtensionValue.fieldNameExtensionValue()` and every per-class
  `FIELD_NAME_EXTENSION_VALUE` constant are deleted;
  [Extension.java:164](../modules/fhir-structure/java/blaze/fhir/spec/type/Extension.java:164)
  calls `value.serializeJsonPolymorphicField(generator, VALUE)` with one shared
  `VALUE` prefix.
- [type_metadata.clj:64-68](../modules/fhir-structure/src/blaze/fhir/type_metadata.clj:64)
  no longer builds per-property `FieldName` tables — registry construction gets
  smaller and faster.

**Accepted trade-off: the writer stops validating choice types.** Today a
hand-constructed value whose type is not in the element's allowed set throws
`Unsupported type … for polymorphic property …` at write time; with value
dispatch it would serialize under a plausible-but-wrong field name. This is
consistent with the rest of the data model — `assoc` silently ignores unknown
keys, and strictness lives on the construction path (the parser rejects unknown
properties; `TypeMetadata.create` rejects unknown keys). A value of a wrong
FHIR type in a choice slot can only come from hand-built production code, which
the round-trip tests cover. Decision: drop the check; do not keep the type
tables just to police it.

### Separator handling — the one piece of state that must stay

JSON needs a comma between object fields and between array elements, and only
there. Jackson derives it from the full write-context state machine; the
replacement splits it by who can know it:

- **Object fields:** only the generator can know whether a field is the first
  one written, because null properties are skipped by every caller. Keep one
  `long` bitstack + `int` depth: `writeStartObject` pushes a first-field bit,
  `writeFieldName` tests-and-clears it or writes `','`. Two branches and bit
  ops, no allocation, no status codes. 64 levels of object nesting is far
  beyond any real resource (contained resources nest a handful of levels);
  throw on overflow rather than growing.
- **Array elements:** the callers *do* know. Arrays are written in exactly
  three places — `Primitive.serializeJsonPrimitiveList`,
  `Complex.serializeJsonComplexList` and the extended-primitive sibling loop —
  all index-based loops. They call `writeElementSeparator()` for `i > 0`, a
  no-op in `CborGenerator`. In return, **every scalar value write
  (`writeString`, `writeNumber`, `writeBoolean`, `writeNull`,
  `writeRawUTF8String`) carries zero checks** — values are the most frequent
  writes, and this is where Jackson's `_verifyValueWrite` disappears without
  replacement.

CBOR needs no separators at all; `writeStartObject` emits one byte.

### JSON byte compatibility

`write-json` output must be byte-identical to today. The rules to replicate
from `UTF8JsonGenerator` with default features:

- **String escaping:** escape `"`, `\` and control characters < 0x20; use the
  short forms `\b \t \n \f \r` where they exist, `\u00XX` (uppercase hex)
  otherwise. Non-ASCII is *not* escaped — it passes through as UTF-8, including
  surrogate pairs. Large values (`Base64Binary` in `Binary.data`/
  `Attachment.data`, `Xhtml` narratives) make the inner loop matter. Structure
  it as **clean-scan + bulk copy**: a first pass over the chunk that only
  answers "any char needing escaping or ≥ 0x80?" — a branch-free OR-reduction
  the JIT can vectorize — then, in the common all-clean case, a straight
  char→byte copy; only a chunk that fails the scan takes the slow escaping
  path. Base64 text never fails the scan, so it moves at copy speed through
  the ordinary `writeString`.

  This fast path is not a base64 special case — it is *the* `writeString`
  loop, and most FHIR string content rides it: every `uri`/`url`/`canonical`
  (`Coding.system`, `Meta.profile`, `Reference.reference` — ASCII by
  construction), ids, codes, oids, uuids, and most `Xhtml` markup. The scan
  works per chunk, so mixed content degrades locally, not wholesale: an
  umlaut in a name or a narrative drops only its own chunk to the slow path,
  and the next chunk goes back through the scan. (Jackson pursues the same intent in
  `UTF8JsonGenerator._writeStringSegment`: a copy loop that breaks on
  `ch > 0x7F || escCodes[ch] != 0`. It differs in both dimensions: the
  data-dependent break keeps that loop scalar where a pure OR-reduction scan
  vectorizes, and once broken, `_writeStringSegment2` runs slow **to the end
  of the segment** — one umlaut early in a buffer-sized segment puts the
  rest of it on the slow path, where this design pays at most one small
  chunk and resumes the fast path at the next.)

  The scan, concretely — every dirty condition folded into the **sign bit**
  of an int expression, OR-reduced, so the loop body is pure ALU work with
  no data-dependent exit:

  ```java
  /**
   * Scans chunk[from..to) and returns true if every char can be copied to
   * the output verbatim: ASCII and no JSON escaping needed.
   * <p>
   * Branch-free: each dirty condition folds into the sign bit, so there is
   * no data-dependent exit and the JIT can vectorize the OR-reduction.
   */
  private static boolean clean(char[] chunk, int from, int to) {
      int acc = 0;
      for (int i = from; i < to; i++) {
          int c = chunk[i];
          acc |= (c - 0x20)         // sign bit set iff c < 0x20  (control char)
               | (0x7F - c)         // sign bit set iff c > 0x7F  (non-ASCII)
               | ((c ^ '"') - 1)    // sign bit set iff c == '"'  (zero-test trick)
               | ((c ^ '\\') - 1);  // sign bit set iff c == '\\'
      }
      return acc >= 0;              // sign bit of acc = "anything dirty?"
  }

  private void writeStringChunk(char[] chunk, int len) throws IOException {
      if (clean(chunk, 0, len)) {
          int p = pos;
          byte[] buf = buffer;
          for (int i = 0; i < len; i++) {
              buf[p + i] = (byte) chunk[i];   // narrowing copy — vectorizes well
          }
          pos = p + len;
      } else {
          writeStringChunkSlow(chunk, len);   // escapes, 2/3-byte UTF-8, surrogates
      }
  }

  // driver: fill a reusable char[] chunk (256–512 chars) via s.getChars(...)
  // and feed it chunk by chunk — which is also what makes resuming after a
  // dirty chunk free: the next chunk simply goes through clean() again.
  ```

  The zero-test trick: `c ^ '"'` is zero only for `c == '"'`, and `0 - 1`
  sets the sign bit; any other char yields ≥ 1 and stays non-negative.
  `(0x7F - c)` deliberately leaves `0x7F` (DEL) clean — Jackson does not
  escape it either. Four implementation notes:

  - **`char[]` via `getChars`, not `charAt`:** SuperWord is far more
    reliable over plain array loads than over `charAt` (which hides a
    compact-strings branch), and the `getChars` fill is itself a vectorized
    intrinsic. The chunk size doubles as the resume granularity.
  - **No lookup table:** the obvious `acc |= ESCAPE_TABLE[c]` kills
    vectorization — a per-element table load is a gather. The arithmetic
    form is element-wise ALU ops feeding an OR-reduction.
  - **Fields hoisted into locals** (`p`, `buf`): C2's alias analysis can
    usually hoist them itself for this body, but locals make it guaranteed —
    immune to non-inlined calls in sibling paths forcing field reloads, and
    the written cursor accumulates in a register with a single store back
    instead of a per-iteration field store pinned by precise-exception
    semantics. Same idiom as Jackson's `_writeStringSegment` and the JDK's
    own string internals.
  - **Verify, don't assume:** whether C2 actually vectorizes the reduction
    is checked in build-order step 3 with JMH `-prof perfasm` (look for
    `vpor`/`vpsub` in the hot loop). The fallback position is still sound:
    a scalar scan is ~4 predictable ALU ops per char with no unpredictable
    branch, and the narrowing copy loop vectorizes dependably either way.

  The same scan serves CBOR: an all-clean chunk is all-ASCII, so byte length
  equals char count and the definite-length header is known without a
  separate counting pass.

  **Considered and rejected: a trusting `writeRawString(String)` for
  base64.** (Jackson's `writeRawUTF8String` cannot serve here anyway: it
  takes a `byte[]`, and obtaining one from the value `String` allocates the
  full value — the very thing `writeString` must avoid.) A raw variant on the
  own generator would skip the escape check by *trusting* that the value is
  clean — an invariant that is usually true (the parser regex-validates
  base64Binary when `use-regex` is on) but not absolute: `use-regex` is a
  context option, and hand-built values (`type/base64Binary`, `assoc :value`)
  accept any `String`. The failure modes are asymmetric and bad: in JSON a
  stray `"` produces a malformed *response*; in CBOR a non-ASCII char breaks
  the length header and produces a corrupt *stored entry*. The existing
  `writeRawUTF8String` user is different in kind — `DateTimeBuffer` hands
  over bytes it constructed itself, so the trust is structural, not
  data-dependent. With the clean-scan fast path, `writeString` reaches raw
  speed on clean input without any trusted invariant, so the raw variant
  buys nothing worth that risk.
- **Numbers:** `int`/`long`/`BigDecimal` formatted exactly as their
  `toString()` — Jackson's defaults (`WRITE_BIGDECIMAL_AS_PLAIN` is off). The
  first pass literally calls `toString` and ASCII-encodes the result; see the
  number-formatting row in "Risk assessment" for why.
- **`writeRawUTF8String`:** quotes around the given bytes, no escaping — the
  contract `DateTimeBuffer` relies on.
- **No root-value separator, no pretty printing.**
- **`close()`:** flush the buffer and close the target stream. Jackson's
  `AUTO_CLOSE_TARGET` is on today, and
  [output.clj:43](../modules/rest-util/src/blaze/middleware/fhir/output.clj:43)
  streams responses through `with-open` on the generator — keep the behaviour
  identical.

### CBOR byte compatibility

`write-cbor` output should be byte-identical to Jackson's `CBORGenerator` with
default features:

- `writeStartObject`/`writeEndObject` → indefinite-length map, `0xBF` … `0xFF`;
  `writeStartArray`/`writeEndArray` → `0x9F` … `0xFF`.
- Integers with minimal-width encoding (`WRITE_MINIMAL_INTS` is default-on) —
  major types 0/1.
- Strings (field names, `writeString`, `writeRawUTF8String`) as
  definite-length text strings with minimal-width length headers.
- **Long strings are the hard case:** the definite-length header needs the
  UTF-8 byte count *before* the bytes, and `Binary.data`/`Attachment.data`
  values can be megabytes. Jackson's `CBORGenerator` switches to CBOR's
  chunked form (an indefinite-length string containing definite-length
  chunks) once a string no longer fits its encoding buffer, with a specific
  threshold and chunk size. Replicate whatever it does — pinned
  *empirically* through the differential tests with multi-megabyte values,
  not from documentation — because stored bytes must compare equal in the
  A/B tests, and the threshold determines where the encoding flips.
  Alternative worth deciding during implementation: sidestep chunk
  replication entirely by always writing **definite-length** text after a
  UTF-8 length pre-scan (one cheap counting pass, no allocation; for
  all-ASCII input — base64 always — the same vectorizable clean-scan as in
  JSON proves byte length = `length()`, so the header is known without
  counting). Simpler
  writer, parseable by every Blaze version — CBOR parsers accept
  definite-length text of any size — but the stored bytes diverge from
  Jackson's above the chunking threshold, so the A/B assertion for oversized
  strings would weaken from byte equality to parse-back equality. A
  contained, documented divergence; byte-value-based hashes are unaffected
  either way.
- `BigDecimal` as tag 4 decimal fraction (`[exponent, mantissa]`, mantissa as
  bignum tag 2/3 when it exceeds long range).
- `true`/`false`/`null` → `0xF5`/`0xF4`/`0xF6`. No self-describe tag.

Strictly, byte identity is not a *correctness* requirement for CBOR: content
hashes are computed from the value via `hashInto`, never from the stored
bytes, and the parser accepts any valid encoding — so old and new bytes for
the same resource coexist harmlessly under the same hash key in the resource
store. Byte identity is still the implementation target because it makes the
A/B test trivial and eliminates any risk of an encoding the parser handles
differently. Once the generator is ours, switching to definite-length maps
(smaller stored resources) becomes a cheap follow-up — noted under "Open
questions", not part of this change.

### The Jackson adapter — a permanent differential-testing oracle

The new abstract class is semantically a subset of Jackson's, so an adapter
from it *to* Jackson is a page of trivial delegation — and because Jackson's
`CBORGenerator extends JsonGenerator`, **one adapter class serves both
backends**, wrapping whichever Jackson generator the test hands it:

```java
public final class JacksonAdapterGenerator extends JsonGenerator {

    private final com.fasterxml.jackson.core.JsonGenerator delegate;

    // writeStartObject()                -> delegate.writeStartObject()   … etc.
    // writeFieldName(name)              -> delegate.writeFieldName(name.toString())
    // writeExtendedFieldName(name)      -> delegate.writeFieldName("_" + name)
    // writeFieldName(prefix, sfx)       -> delegate.writeFieldName(prefix + sfx)
    // writeExtendedFieldName(pfx, sfx)  -> delegate.writeFieldName("_" + pfx + sfx)
    // writeString(EncodedName v)        -> delegate.writeString(v.toString())
    // writeElementSeparator()           -> no-op — Jackson's context machine
    //                                      writes the commas itself
    // writeRawUTF8String(b, o, l)       -> delegate.writeRawUTF8String(b, o, l)
}
```

This turns the migration-time A/B harness into **permanent property-based
tests**: every serialization runs twice — once through
`Utf8JsonGenerator`/`CborGenerator`, once through the adapter over Jackson —
and the byte streams must be equal. The comparison stays in the test suite
forever, not just during migration, and it costs nothing to keep:
`jackson-core` and `jackson-dataformat-cbor` remain production dependencies
for the parser, and the adapter is one small class in `java/` (the module has
a single Java source root; the class is test-only by use, not by classpath —
acceptable, since it pulls in nothing that isn't already there).

Two rules keep the oracle honest:

1. **The adapter must not reuse the precomputed bytes.** It decodes
   `EncodedName` back to a `String` (the interner keeps the original) and lets
   Jackson do its own quoting, encoding and length headers. If it copied
   `jsonQuoted`/`cbor` byte arrays through, a bug in the `EncodedName`
   precomputation would infect both sides and the comparison would prove
   nothing. With the rule, the Jackson side is exactly today's writer.
2. **`writeElementSeparator` as a no-op is the point, not a gap.** Jackson
   re-derives commas from its own state machine, so a misplaced or missing
   explicit separator in one of the three list helpers — or a first-field-bit
   bug in `Utf8JsonGenerator` — shows up as a byte difference on the first
   affected value. Better still, the adapter *restores Jackson's structural
   validation at test time*: a serializer that ever produced an invalid call
   sequence (value without field name, unbalanced start/end) makes the adapter
   run throw, giving back exactly the checking this design removes from
   production, where it belongs — in tests.

What the oracle proves, and what it doesn't: it proves that for every call
sequence the serializers actually make, the two encoders produce identical
bytes — encoding equivalence, separator placement, structural validity. It
does **not** prove the serializers call the right methods with the right data;
a wrong field written by a serializer agrees on both sides. That class of bug
is unchanged by this design and stays covered by the existing round-trip and
golden-file tests. `EncodedName`'s precomputed variants, bypassed by rule 1,
get their own tests — behavioral ones, since the arrays are not accessible
(see "`EncodedName`"): write a name through each generator and compare the
bytes against `SerializedString.asQuotedUTF8()` / a Jackson-written name.

The module's tests already use `clojure.test.check`; the generators the
comparison needs (adversarial strings, boundary numbers, whole resources) slot
into that infrastructure, plus a fixed corpus: the KDS bundle and every
resource in the test suite.

## Risk assessment

The risk profile of hand-writing two encoders is unusually favorable, for one
reason: **a perfect oracle exists.** The output is a deterministic byte stream,
Jackson produces the reference stream today, and the adapter above lets the
same serialization code drive both. Differential property-based testing with
byte equality is close to the strongest verification available for this kind
of code — every failure mode below is *deterministically* visible as a byte
difference once the input corpus reaches it, so the residual risk collapses to
"inputs the generators never see", which the test generators control.

### `Utf8JsonGenerator`

Broad but shallow risk: many small rules, each individually testable, and the
strongest oracle (Jackson re-validates structure in the adapter run).

| Failure mode | Inherent risk | Why / mitigation |
| --- | --- | --- |
| String escaping divergence — short forms `\b \t \n \f \r` vs `\u00XX`, uppercase hex, non-ASCII passthrough | **medium-high** | The most rule-dense part. Generative strings must cover all 32 control characters, `"` and `\`, and 2/3/4-byte UTF-8; each divergence is a deterministic byte diff. |
| Malformed input: lone/inverted surrogates in a `String` | medium | Rare in practice (parsed resources are valid), reachable from hand-built values. Decide the behaviour = whatever Jackson does (throw), and pin it with explicit tests rather than reading it off the docs. |
| Buffer-straddle bugs — a token spanning the internal buffer boundary | **medium** | The classic encoder bug, invisible at the default 8 KiB unless a test value is large. Mitigation: the buffer size is a constructor parameter, and the property tests run the whole corpus at sizes {1, 2, 3, 7, 8, 13, 8192} so every token straddles in some run. |
| Separator logic — comma before skipped-null fields, first-field bitstack | medium | Fully covered by the adapter oracle: Jackson inserts its own commas, so any divergence surfaces on the first affected field. Depth > 64 throws explicitly; add a nesting-depth test at the limit. |
| Number formatting | **low** | Do not hand-roll a digit writer in the first pass: call `Integer.toString`/`Long.toString`/`BigDecimal.toString` and ASCII-encode the result — bit-identical to Jackson's defaults by construction, `Integer.MIN_VALUE` and exponent-form `BigDecimal`s included. Optimize later only if profiles say so, under the same tests. |
| `writeRawUTF8String`, `flush`/`close` semantics | low | Trivial; close-order pinned by one test against the streaming middleware contract. |

### `CborGenerator`

Narrow but sharp risk: no escaping and no separators — structurally the
simpler encoder — but the numeric encodings have exact-boundary conventions
that are easy to get subtly wrong and impossible to notice without byte
comparison.

| Failure mode | Inherent risk | Why / mitigation |
| --- | --- | --- |
| Minimal-int width boundaries — 23/24, 2⁸, 2¹⁶, 2³², and the negative encoding (major type 1 encodes −1−n) | **high if hand-derived** | Classic off-by-one territory. Property tests generate boundary-adjacent values on both sides of every width step, for `int` and `long`; each mistake is a deterministic diff against Jackson. |
| `BigDecimal` as tag 4 — exponent = −scale sign convention, mantissa as minimal int vs bignum (tag 2/3), negative bignum −1−n | **highest single item** | Several stacked conventions. Pin the exact behaviour *empirically* via the adapter, not by reading the CBOR spec — the requirement is "what Jackson writes", including any Jackson quirks. Generate decimals with huge mantissas, negative scales, and scales beyond int range. |
| String length header counts UTF-8 **bytes**, not chars | medium | The classic multi-byte error; any non-ASCII generative string catches it instantly. |
| Long-string chunking — the definite→chunked flip for `Binary.data`/`Attachment.data`-sized values: threshold, chunk size, chunk headers | **high** | Invisible below the threshold, so ordinary corpora never reach it. Mitigation: generative string lengths straddling the flip point on both sides, plus multi-megabyte base64 corpus values, all byte-compared against Jackson. Or avoid chunking altogether via the always-definite-length option in "CBOR byte compatibility". |
| Indefinite-length containers `0xBF`/`0x9F`/`0xFF` | low | Single fixed bytes. |
| Buffer straddling | medium | Same mitigation as JSON: run the corpus at tiny buffer sizes. |

Two structural facts shrink the CBOR surface further: the interface omits
everything Blaze never writes (no `double`/`float`, no binary as CBOR byte
strings — `Base64Binary` stays base64 *text*, exactly as Jackson writes the
`String` today — and no non-string keys), so those encodings are simply not
implemented; and a second, independent oracle exists — everything written must
parse back to an `equiv` value through the existing Jackson `CBORParser`,
which catches any malformation the byte corpus misses.

### Verdict

Getting either implementation *initially* wrong somewhere is likely — that is
the nature of hand-written encoders, and the boundary-condition items above
are near-certain to bite at least once during development. Getting them wrong
*undetected* is what matters, and with the permanent differential oracle, tiny
forced buffer sizes, boundary-value generators and the parse-back round-trip,
the escape routes for an undetected bug are narrow: it would need an input
class that production reaches but the generators don't. The two blind spots to
respect are shared-fate bugs (rule 1 above — keep the adapter off the
precomputed bytes) and serializer-level bugs, which are out of this change's
scope and covered where they always were. Ongoing risk is also low: the
generators are write-once code with no feature growth (the interface is closed
over what FHIR serialization needs), and the permanent oracle turns any future
touch into a byte-compared change.

## What is deleted, what survives

Deleted:

- all `com.fasterxml.jackson.core.JsonGenerator` imports on the write path
  (78 files — signature swap, method names unchanged)
- `SerializableString`/`SerializedString` on the write path (23 imports)
- the `Keyword[]`/`FieldName[]` tables in `PolymorphicPropertyHandler`
- `FieldName` and its interner — every property holds one `EncodedName`; the
  `_name` form is written by `writeExtendedFieldName`
- `ExtensionValue.fieldNameExtensionValue()` and ~40 per-class
  `FIELD_NAME_EXTENSION_VALUE` constants
- `json-factory`/`cbor-factory` use on the write path (`resource.clj`) — the
  parser keeps both factories

Survives:

- the whole read path: Jackson `JsonParser`, `CBORParser`,
  `StreamReadConstraints`, `parse-json`, `parse-cbor`
- the `jackson-core` and `jackson-dataformat-cbor` dependencies (parsing)
- `hash.clj`'s databind `JsonSerializer` for `Hash` (admin-api's jsonista
  pipeline)
- `PropertyHandler` and its subclasses, `PropertyIndex`, `TypeMetadata`, the
  four `*Map` classes — all keep their roles, with signatures moved to the new
  generator

New, permanent: `JacksonAdapterGenerator` — the differential-testing oracle,
one class serving both backends, test-only by use.

## Compatibility constraints

Invariants with tests that must exist before the corresponding code:

- `write-json` produces byte-identical output to the Jackson-based writer for
  every resource in the KDS bundle, and for generative inputs covering: control
  characters and `"`/`\` in strings, non-ASCII (2/3/4-byte UTF-8, surrogate
  pairs), extended primitives (`_name`), extended primitive *lists* with null
  placeholders, polymorphic properties of every kind (primitive, complex,
  extended → `_valueBoolean`), `Xhtml`, multi-megabyte `Base64Binary` values
  (`Binary.data`, `Attachment.data`) and string lengths straddling the CBOR
  chunking threshold, all number types including negative and boundary values
  and `BigDecimal` scales, deeply nested contained resources.
- `write-cbor` likewise byte-identical, same corpus — unless the
  always-definite-length option in "CBOR byte compatibility" is chosen, in
  which case oversized strings assert parse-back equality instead and
  everything else stays byte-identical.
- Everything `write-json` produces parses back to an `equiv` value via the
  existing Jackson-based parser (and the same for CBOR) — the round-trip
  guards against any divergence the byte comparison corpus misses.
- Content hashes are untouched by construction (hashing never sees the
  generator), but the KDS hash harness from the map-classes work must stay
  green.
- The differential comparison against Jackson is **permanent**, via
  `JacksonAdapterGenerator`: every generative and corpus test writes through
  both the native generators and the adapter and asserts byte equality. It is
  never deleted — Jackson remains a production dependency for the parser, so
  keeping the oracle costs nothing.

## Build order

Test-driven, per `AGENTS.md`. Each step green before the next. One issue, one
commit (amended).

1. **Differential harness first.** The abstract `JsonGenerator` class (which
   this step introduces), the `JacksonAdapterGenerator` extending it, and a
   test utility that applies the same *scripted call sequence* to a native
   generator and to the adapter and compares bytes — it must be able to fail
   before it can pass. Call scripts are the right vehicle before step 5,
   because the serializers can't drive the new abstraction until their
   signatures move; `test.check` generates the scripts (values, names,
   nesting) and the fixed corpus supplies realistic sequences.
2. `EncodedName` (+ interner, ASCII assertion). Test the precomputed variants
   behaviorally through generator output — the arrays have no accessors (see
   "`EncodedName`").
3. `Utf8JsonGenerator`: scalars and escaping first (`writeString`,
   `writeNumber`, `writeBoolean`, `writeNull`, `writeRawUTF8String`), then
   object/field/separator handling, then `writeExtendedFieldName` and the
   two-part `writeFieldName`/`writeExtendedFieldName`. Verify with JMH
   `-prof perfasm` that the clean-scan reduction and the narrowing copy loop
   actually vectorize (see the snippet under "JSON byte compatibility").
   Byte-compare against Jackson at every step.
4. `CborGenerator`, same order, byte-compared against Jackson's
   `CBORGenerator`.
5. Swap the signatures: `serializeAsJsonValue`, `serializeJsonField`,
   `serializeJsonPrimitiveValue/Extension`, the property handlers, the three
   list helpers, `DateTimeBuffer.writeTo`, the `*Map` classes and
   `TypeMetadata.serializeValues` move to `blaze.fhir.writing.JsonGenerator`;
   `FieldName` parameters become `EncodedName`, and `fieldName.extended()`
   call sites become `writeExtendedFieldName`. Delete `FieldName`.
   Mechanical; the A/B harness plus `make -C modules/fhir-structure test` hold
   the line. Force a Java recompile (`clojure -T:build compile`) — `make test`
   uses stale `.class` files otherwise.
6. `typeSuffix()` on `ExtensionValue` and the map classes;
   `serializeJsonPolymorphicField`; shrink `PolymorphicPropertyHandler` to
   `(key, prefix)`; delete `fieldNameExtensionValue` and the per-class
   constants; drop the table construction in `type_metadata.clj`.
7. Rewire `resource.clj` `write-value` to the new constructors; delete the
   write-side factory use.
8. Promote the harness to the permanent suite: whole-resource property tests
   that serialize every generated and corpus resource through the native
   generators and through the adapter and assert byte equality — now driven by
   the real serializers instead of call scripts. Run the whole suite at the
   forced tiny buffer sizes as well as the default.
9. Re-run `spec_test_perf.clj` benchmarks (`bench-write-json` on the KDS
   bundle, the HumanName/CodeableConcept micro-benches) and record the numbers
   in the `comment` block. Re-check `make test-coverage` ≥ 95% forms.

Verification for the whole change:

```
make fmt
make lint
make -C modules/fhir-structure test
make test-coverage
```

No other module changes, but a full `make test` before finishing is cheap
insurance given every FHIR response flows through this code. On a fresh
worktree, `make build-ig` first.

## Expected effect

Honest framing: Jackson's write path is already fast (~233 µs for the 416 KB
KDS bundle), and escaping + buffer copying — the irreducible work — dominates.
The wins are:

- context-machine and `_verifyValueWrite` elimination on every one of the
  ~200k write calls per KDS-bundle write, replaced by one bit test per field
  name and nothing per value,
- field names and `resourceType` as single precomputed `arraycopy`s,
- polymorphic writes without the `:fhir/type` lookup and table scan,
- registry construction without per-property `FieldName` tables (~50-entry
  tables for each open choice element), and a fixed per-type suffix constant
  instead of ~40 per-class `valueX` pairs,
- half the interned name objects: the `_name` variants — built today for every
  primitive property, written almost never — are not materialized at all.

Measure, don't promise: the benchmark comparison in step 9 is the deliverable.
If the result is not clearly ahead of Jackson, the two-part-name and
table-deletion benefits still stand on their own (memory, registry build time,
code deleted), but re-evaluate at step 7, before the production write path
moves off Jackson — that is the cheap point to stop, and the adapter keeps
both paths comparable right up to it.

## Open questions

- **Definite-length CBOR maps/arrays.** Once the generator is ours, the
  `*Map` classes could pre-count non-null slots and write definite-length
  containers — smaller stored resources, still hash-compatible (hashes are
  value-based). Deliberately out of scope: it breaks byte-for-byte A/B testing
  against Jackson, so it should come as its own change with its own
  measurement.
- **Direct `byte[]` output.** `write-json-as-bytes` currently goes through
  `ByteArrayOutputStream`; a growable internal buffer with a `toByteArray()`
  terminal would save one copy. Follow-up, not part of this change.
- **Buffer recycling.** Jackson recycles buffers through a thread-local
  `BufferRecycler`. Start with a plain 8 KiB allocation per generator (one per
  request/store write); add recycling only if allocation shows up in profiles.

## Key file reference

| Concern | Location |
| --- | --- |
| Property handlers (incl. polymorphic) | `modules/fhir-structure/java/blaze/fhir/writing/` |
| `FieldName` (deleted, replaced by `EncodedName`) | `modules/fhir-structure/java/blaze/fhir/spec/type/FieldName.java` |
| `ExtensionValue` / per-class value field names | `modules/fhir-structure/java/blaze/fhir/spec/type/ExtensionValue.java`, `Extension.java:164` |
| Type metadata / handler construction | `modules/fhir-structure/src/blaze/fhir/type_metadata.clj` |
| `TypeMetadata` (serializeValues, resourceType) | `modules/fhir-structure/java/blaze/fhir/spec/type/TypeMetadata.java` |
| Generator creation, factories, public write fns | `modules/fhir-structure/src/blaze/fhir/spec/resource.clj:1240-1299` |
| Date/time raw writing | `modules/fhir-structure/java/blaze/fhir/spec/type/system/DateTimeBuffer.java` |
| The three array-writing helpers | `Primitive.java` (`serializeJsonPrimitiveList`), `Complex.java` (`serializeJsonComplexList`) |
| Response streaming (close semantics) | `modules/rest-util/src/blaze/middleware/fhir/output.clj:43` |
| CBOR store writers | `modules/db-resource-store/src/blaze/db/resource_store/kv.clj:80`, `…-cassandra/src/blaze/db/resource_store/cassandra.clj:83` |
| Hash databind serializer (stays Jackson) | `modules/fhir-structure/src/blaze/fhir/hash.clj:66` |
| Benchmarks | `modules/fhir-structure/test-perf/blaze/fhir/spec_test_perf.clj` |
