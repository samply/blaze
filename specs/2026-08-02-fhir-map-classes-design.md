# Self-serializing, order-preserving FHIR map classes

**Date:** 2026-08-02
**Status:** Design approved, not yet implemented
**Scope:** `modules/fhir-structure`, with follow-on changes in `modules/db`, `modules/interaction`

This document is self-contained. It records the measurements that motivate the
change, the design, the compatibility constraints, and the build order. No prior
conversation is needed to execute it.

## Summary

Replace the plain Clojure maps that represent FHIR resources and backbone
elements with four Java classes — `ElementMap`, `BackboneElementMap`,
`ResourceMap` and `DomainResourceMap`, one per FHIR abstract type — that
implement `Base` (and therefore `IPersistentMap`) and serialize themselves.
Per-type variation lives in a shared metadata instance rather than in generated
classes: **four classes, one data reader tag, 658 metadata instances**.

This document writes **`*Map`** when a statement applies to all four.

The parser builds `*Map` values directly.

## Problem

`AbstractMapTypeHandler.writeProperties`
([AbstractMapTypeHandler.java:46](../modules/fhir-structure/java/blaze/fhir/writing/AbstractMapTypeHandler.java:46))
allocates an `Object[]` slot array on every write, fills it by `kvreduce`-ing the
map through `SlotFiller`, then writes the non-null slots in element-definition
order. The slot array exists only because a Clojure map has no order, so the
canonical FHIR property order has to be reimposed at serialization time.

That work is repeated on every write of every map-represented value, and the
same information — which element each key belongs to — was already computed and
discarded by the parser.

### Measurements

All figures are for the KDS test bundle,
`.github/test-data/kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json`
(416 KB), measured on 2026-08-02.

**Where the time goes.** From the `comment` block in
`modules/fhir-structure/test-perf/blaze/fhir/spec_test_perf.clj`: writing that
bundle takes ~247 µs, reading it takes ~3071 µs. Parsing is roughly 12× the cost
of writing, and every FHIR read request does both (CBOR parse from the resource
store, JSON write to the response).

**Structure of the work.** 667 map-handled values, of which 552 are
`PersistentArrayMap` and 115 are `PersistentHashMap`. Summed over them, the
current design performs 3357 `kvreduce` steps (one per present entry); a design
that instead looks up each possible property would perform 9055 lookups. `P`
(possible properties) exceeds `N` (present entries) by 2.7×. Observation is the
extreme: 32 property handlers against an average of 11.2 entries.

**A rejected alternative.** Replacing the slot array with a loop over
`propertyHandlers` doing `map.valAt(key)` was implemented and benchmarked A/B/A
in separate JVMs (criterium, 60 samples, two rounds each):

| Variant | Round 1 | Round 2 |
| --- | --- | --- |
| slots (current) | 260.2 µs | 261.9 µs |
| valAt loop | 291.3 µs | 288.8 µs |
| slots again | 255.2 µs | 257.3 µs |

The valAt loop is ~12% slower, well outside the ±1.5% machine noise. It loses
because `PersistentArrayMap.valAt` is a linear identity scan and
`PersistentHashMap.valAt` is a trie walk, so it pays O(P) lookups of non-trivial
cost each where the slot design pays O(N) cheap ones. **Do not re-propose this.**
The `*Map` classes make the same idea work only because a shape index turns every
lookup into one array index.

**Memory.** Container-only footprint (leaf values excluded, since they are
identical in both designs), computed with Blaze's own `Base` memSize constants at
4-byte references:

| | bytes |
| --- | --- |
| current (`PersistentArrayMap` / `PersistentHashMap`) | 130,584 |
| all-flat variant (single class, `Object[P]`) | 61,272 |
| **proposed** (the four `*Map` classes) | **56,136** |
| **delta** | **−74,448 (−57.0%)** |

Reusing `AbstractBackboneElement` for the backbone-shaped values is worth
**−5,136 bytes (−14.0%)** on its own, because `id`, `extension` and
`modifierExtension` leave the array and only **4 of the 506** backbone instances
in this bundle need a real `ExtensionData` object — the other 502 share the
interned `EMPTY` singleton. It also removes the all-flat variant's worst
regressions: `Bundle.entry.request` goes from +12.5% against a plain map to
exactly break-even, and `Observation.component` from +11.9% to −13%.

The win comes from `MEM_SIZE_PERSISTENT_HASH_MAP_ENTRY = 64` bytes per entry
([Base.java:90](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:90)).
`RT/mapUniqueKeys` switches to `PersistentHashMap` above 8 entries, which every
real resource exceeds. Observation costs 56 + 64×11.2 ≈ 773 bytes of container
today versus 24 + `Object[32]` = 168 bytes proposed (−76%). DiagnosticReport
−85%, MedicationStatement −84%, Specimen −82%.

Sparsely populated wide types still regress — `Consent.provision`, `Media`,
`EvidenceVariable` set few of many possible properties, so `Object[P]` costs more
than a small `PersistentArrayMap`. These are a small minority of instances and
are far outweighed; re-measure after implementation rather than designing around
them.

Memory matters because parsed resources are held in a Caffeine cache weighed by
`Base/memSize`
([resource_cache.clj:119-122](../modules/db/src/blaze/db/resource_cache.clj:119)).
Container savings translate directly into cache capacity.

## Design

### The four map classes

Four classes in package `blaze.fhir.spec.type`, one per FHIR abstract type, all
implementing `Complex` (hence `Base`, hence `IPersistentMap`, `IKeywordLookup`,
`java.util.Map`, `IObj`, `IHashEq` — see
[Base.java:13](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:13)).

Every one has the same shape: the abstract type's properties as explicit typed
fields, then a shared `TypeMetadata` reference, then an `Object[]` holding the
remaining type-specific properties in element order. Which class a FHIR type gets
is decided by its leading properties, which are fixed per shape and were verified
against the writing context on 2026-08-02.

| Class | Extends | Leading properties | Types | Values in KDS bundle |
| --- | --- | --- | --- | --- |
| `ElementMap` | `AbstractElement` | `id extension` | 9 | 0 |
| `BackboneElementMap` | `AbstractBackboneElement` | `id extension modifierExtension` | 464 | 506 |
| `ResourceMap` | — | `id meta implicitRules language` | 3 | 1 |
| `DomainResourceMap` | `ResourceMap` | + `text contained extension modifierExtension` | 143 | 160 |

**`BackboneElementMap extends AbstractBackboneElement implements Complex`** — for
the 464 backbone-element types, 506 of the 667 values in the KDS bundle.

```java
// inherited: ExtensionData extensionData  (id, extension, meta)
// inherited: List<Extension> modifierExtension
private final TypeMetadata metadata;   // shared per FHIR type
private final Object[] values;         // type-specific properties, element order
```

This works because backbone element order is *exactly* `id, extension,
modifierExtension`, which is precisely what `AbstractElement.serializeJsonBase`
plus `AbstractBackboneElement.serializeJsonBase` emit. `Dosage` and `Timing` are
already `AbstractBackboneElement` + `Complex`, so the pattern is proven.

This bucket holds both anonymous elements nested in resources and the six named
complex types that have no Java implementation and carry `modifierExtension`:
`ElementDefinition` (37 properties), `MarketingStatus`, `Population`,
`ProdCharacteristic`, `ProductShelfLife`, `SubstanceAmount`.

**`ElementMap extends AbstractElement implements Complex`** — for the 9
`Element`-shaped types, whose order is `id, extension` with no
`modifierExtension`. Verified 2026-08-02, these are exclusively *anonymous
elements nested inside complex types* — 8 within `ElementDefinition`
(`base`, `binding`, `constraint`, `example`, `mapping`, `slicing`,
`slicing.discriminator`, `type`) and `SubstanceAmount.referenceRange`. That is
FHIR's own rule surfacing: elements nested in a complex type are `Element`,
elements nested in a resource are `BackboneElement`.

They must **not** share `BackboneElementMap` with an always-empty
`modifierExtension` field. `AbstractBackboneElement.valAt` returns
`modifierExtension` unconditionally
([AbstractBackboneElement.java:45-48](../modules/fhir-structure/java/blaze/fhir/spec/type/AbstractBackboneElement.java:45)),
so `(:modifierExtension slicing)` would yield an empty vector where a plain map
yields `nil`, and `contains?` would answer `true` where a plain map answers
`false` — `Base.containsKey` is `valAt(key) != null`. The property-based tests in
build step 3 would fail on it. These types are not obscure: Blaze stores
StructureDefinitions, so `ElementDefinition` values are really parsed, hashed and
served.

`ElementMap` and `BackboneElementMap` duplicate the `metadata` and `values`
fields, because Java single inheritance forces different parents. Two duplicated
fields is the right price for not special-casing `valAt`.

**`ResourceMap implements Complex`** — for `Bundle`, `Parameters` and `Binary`,
the three types that are `Resource` but not `DomainResource`.

```java
private final String id;               // System.String
private final Meta fhirMeta;           // the FHIR `meta` element
private final Uri implicitRules;
private final Code language;
private final IPersistentMap objMeta;  // Clojure IObj metadata — NOT the FHIR meta
private final TypeMetadata metadata;   // shared per FHIR type
private final Object[] values;         // remaining properties, element order
```

**`DomainResourceMap extends ResourceMap`** — for the other 143 resource types,
adding the four `DomainResource` elements:

```java
private final Narrative text;
private final List<?> contained;
private final List<Extension> extension;
private final List<Extension> modifierExtension;
```

Resources cannot extend `AbstractElement`: a FHIR resource is not an `Element`,
and their element order is `id, meta, implicitRules, language, text, contained,
extension, modifierExtension`, so `AbstractElement.serializeJsonBase` — which
writes `id` then `extension` adjacently — would emit `extension` second instead of
seventh and break byte-identical output. Writing the eight fields in declaration
order across the two classes produces the correct sequence directly.

> **Naming hazard.** `ResourceMap` carries *two* things called meta: the FHIR
> `meta` element (a `Meta`, reachable as `valAt(:meta)`) and the Clojure `IObj`
> metadata (reachable as `meta()`). They are unrelated and must never be
> conflated. `blaze.db.node/enhance-resource` attaches `:blaze.resource/hash`,
> `:blaze.db/tx`, `:blaze.db/op` and `:blaze.db/num-changes` to the *IObj* one via
> `with-meta` ([node.clj:186-198](../modules/db/src/blaze/db/node.clj:186)), and
> `blaze.interaction.history.util` and `blaze.interaction.search.util` read them
> back; meanwhile `(update resource :meta ...)` at
> [node.clj:197](../modules/db/src/blaze/db/node.clj:197) and
> [resource.clj:1047](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:1047)
> targets the FHIR one. Name the fields distinctly, as above. `assoc`, `dissoc`
> and the custom `select-keys` must carry the IObj metadata through.
> `ElementMap` and `BackboneElementMap` avoid the collision entirely: they have no
> FHIR `meta` element, and `ExtensionData` already holds the IObj metadata, which
> `AbstractElement.meta()` returns.

All four use `Object[]`, not `Base[]`: repeating properties hold
`PersistentVector`, which cannot implement `Base` (see "The two non-Base
leaves").

### Immutability and safe publication

The `*Map` classes are **immutable values**, like every other FHIR type in
Blaze. `IPersistentMap` requires it, and it is load-bearing here rather than
merely idiomatic: parsed resources are shared across threads through the Caffeine
resource cache, so instances are read concurrently by request threads that never
synchronise with the parser thread that built them.

Concretely:

- **Every field is `final`**, including `values`.
- **The `values` array is never mutated after construction** and never escapes.
  No accessor returns it; `seq`, `iterator`, `entrySet` and `kvreduce` read from
  it without handing it out.
- **`assoc` and `dissoc` copy the array.** `Base.without` is `assoc(key, null)`,
  so `dissoc` is the same copy.

> **The parser must fill a local array and pass it to the constructor — never
> construct first and fill afterwards.** The final-field freeze covers the array
> *elements*, but only those written before the constructor returns. Populating
> the array afterwards would leave the value safe only where the publication path
> happens to carry its own barrier — true of Caffeine and `CompletableFuture`
> today, but not something that can be verified at the point where the bug lives,
> nor relied on as the code changes.
>
> This is the one place where the parser rewrite in "Parser integration" can
> introduce a data race, and single-threaded tests cannot detect it.

**Copy cost.** Because `assoc` copies the whole array, the design assumes no FHIR
type has a very large property count. Measured across all 619 map-represented
types on 2026-08-02:

| | P |
| --- | --- |
| maximum | **54** (`ActivityDefinition`; then `ExplanationOfBenefit` 51, `Measure` 49) |
| mean | 11.5 |
| median | 7 |
| types with P > 50 | 2 |
| types with P > 40 | 8 |
| types with P > 30 | 40 |

So the worst single `assoc` copies 54 references — 216 bytes via
`System.arraycopy`. That is not obviously worse than today: `PersistentArrayMap`
already copies its entire `2N` array on every `assoc`, which covers 552 of the
667 values in the KDS bundle, and after the leading properties move into fields
the backbone arrays are usually *smaller* than the array maps they replace.
Against `PersistentHashMap` the new design copies roughly twice as many
references for the widest resources, but as one flat `arraycopy` with one
allocation instead of a trie path copy with pointer chasing.

The case to watch is repeated `assoc` in a loop, which is O(n·P) where the trie
was O(n·log P). `blaze.interaction.transaction.bundle.links` is the only such
walker, and it only `assoc`es properties whose value actually changed
([links.clj:57](../modules/interaction/src/blaze/interaction/transaction/bundle/links.clj:57)),
which in practice is the handful carrying references. Even the degenerate case —
every property of an `ActivityDefinition` changing — is 54² ≈ 2,900 reference
copies, about 11 KB. Do not add a transient or builder path pre-emptively;
measure `links.clj` after implementation and only then decide.

### Keyword lookup and `ILookupThunk`

`Base` extends `IKeywordLookup` and declares
`default ILookupThunk getLookupThunk(Keyword) { return null; }`
([Base.java:276-278](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:276)).
This matters more than `valAt` does: Clojure compiles a keyword callsite such as
`(:id resource)` into a `KeywordLookupSite` with an inline cache, and if the
target is an `IKeywordLookup` that returns a thunk, subsequent calls invoke
`thunk.get(target)` and **bypass `valAt` entirely**. A thunk signals a miss by
returning itself, which makes the callsite fall back to `RT.get`.

Blaze already relies on this. `ResourceHandle` has seven thunks, and
`AbstractBackboneElement` puts one on an *abstract* base class so its
`instanceof AbstractBackboneElement` guard stays true for every concrete subtype
([AbstractBackboneElement.java:20-25](../modules/fhir-structure/java/blaze/fhir/spec/type/AbstractBackboneElement.java:20)):

```java
private static final ILookupThunk MODIFIER_EXTENSION_LOOKUP_THUNK = new ILookupThunk() {
    @Override
    public Object get(Object target) {
        return target instanceof AbstractBackboneElement e ? e.modifierExtension : this;
    }
};
```

The `*Map` classes must implement `getLookupThunk` for the hot properties, or they will
be a regression at every `(:id resource)` and `(:meta resource)` callsite. It can
do so with guards at least as broad as a class hierarchy would give, because
**the leading properties occupy fixed slot indices**. Measured across all 619
map-represented types on 2026-08-02:

| Key | Slot index |
| --- | --- |
| `:id` | **0 for all 619 types** |
| `:meta` | 1 for 146 types, absent for 473 |
| `:extension` | 1 for 473, 6 for 143, absent for 3 |
| `:modifierExtension` | 2 for 464, 7 for 143, absent for 12 |

Element order puts inherited properties first and they are identical within each
abstract shape, so the index is a constant, not a per-type lookup:

With explicit leading fields the thunks read a field directly, and each guard
covers a whole abstract branch:

```java
// :id on ResourceMap — the guard also covers DomainResourceMap, so all 146
// resource types share one thunk
private static final ILookupThunk ID_LOOKUP_THUNK = new ILookupThunk() {
    @Override
    public Object get(Object target) {
        return target instanceof ResourceMap r ? r.id : this;
    }
};

// :meta — the FHIR meta element, never the IObj metadata
private static final ILookupThunk META_LOOKUP_THUNK = new ILookupThunk() {
    @Override
    public Object get(Object target) {
        return target instanceof ResourceMap r ? r.fhirMeta : this;
    }
};
```

`ElementMap` and `BackboneElementMap` need no `:id` or `:extension` thunk of
their own: the `Element` interface already provides both, guarded on
`instanceof Element`, which also covers all 39 Java complex types
([Element.java:29-34](../modules/fhir-structure/java/blaze/fhir/spec/type/Element.java:29)).
`BackboneElementMap` likewise inherits `:modifierExtension` from
`AbstractBackboneElement`. So the leading-field thunks each cover a whole
abstract branch, which is what keeps polymorphic callsites fast.

#### Every property gets a thunk

Thunks are not reserved for hot properties. `Coding` implements one for all six
of its properties plus `:fhir/type`, and that is the convention to follow. Since
the `*Map` classes generate their thunks rather than hand-writing them, covering
every property costs one method, not one per property:

```java
@Override
public ILookupThunk getLookupThunk(Keyword key) {
    int slot = metadata.slot(key);              // discriminated: <0 means a field
    if (slot >= 0) return metadata.thunk(slot); // precomputed, see below
    return super.getLookupThunk(key);           // leading fields, inherited
}
```

The array-slot thunk closes over the slot index:

```java
// built once per (type, slot) and cached in TypeMetadata
final TypeMetadata md = ...;
final int slot = ...;
new ILookupThunk() {
    @Override
    public Object get(Object target) {
        return target instanceof DomainResourceMap m && m.metadata == md
            ? m.values[slot] : this;
    }
};
```

> **The `m.metadata == md` guard is a correctness requirement, not an
> optimisation.** For the hand-written types, `instanceof Coding` fully
> determines the layout. Here it does not: all 143 DomainResource types share one
> Java class, so the *metadata* determines which property lives at which slot.
> A thunk guarded on `instanceof DomainResourceMap` alone would, at a callsite
> specialised for `Observation`, happily read `values[i]` out of a `Patient` and
> return a completely unrelated property. No exception, no type error — just a
> wrong value. This is the most dangerous single line in the design and needs a
> dedicated test that pushes a second type through a callsite specialised for a
> first.

**Precompute the thunks in `TypeMetadata`.** When a thunk's guard fails it
returns itself, which makes the callsite re-fault and call `getLookupThunk`
again. If that allocated a fresh thunk each time, a callsite alternating between
two types would allocate on every call — worse than plain `valAt`. Holding an
`ILookupThunk[]` on the metadata, indexed by slot, makes `getLookupThunk` an
array read and the pathological case merely slow rather than allocating.

Thrashing should be rare in practice: type-specific properties are read by
type-specific code, and the cross-type keys (`:id`, `:meta`, `:extension`) are
leading fields whose guards span a whole abstract branch. Generic walkers such as
`fhir_path.clj` use dynamic keys through `get`, which never engage thunks at all.

### Considered and partly adopted: a class per abstract type

An alternative shape — `Resource`, `DomainResource`, `BackboneElement` and
`Element` base classes holding their leading properties as explicit typed fields,
with only the type-specific properties in the array. Both premises behind it are
correct, but the payoff is not there.

**The abstract-type partition is real and clean.** Of the 619 map-represented
types (658 total minus the 39 Java complex types, which have no property
handlers), every one falls into exactly four leading-property shapes:

| Shape | Leading properties | Types |
| --- | --- | --- |
| `BackboneElement` | `id extension modifierExtension` | 464 |
| `DomainResource` | `id meta implicitRules language text contained extension modifierExtension` | 143 |
| `Element` | `id extension` | 9 |
| `Resource` | `id meta implicitRules language` | 3 |

**`id` really is the only `System.String`.** Across all 619 types there are
exactly 619 `StringPropertyHandler`s, one per type, and every one is `:id`.
(`Extension.url` is the other `System.String` in R4, but `Extension` has a Java
implementation and so never reaches a property handler.)

The strongest argument *for* it is `ILookupThunk`: explicit fields let a thunk
guard on `instanceof ResourceMap`, which stays true across all 146 resource
types, so a generic `(:id resource)` callsite never degrades. A naive
single-class thunk would have to guard on `metadata == <captured>` and would
thrash at exactly those polymorphic callsites.

That argument turns out not to be decisive on its own, because the leading
properties occupy fixed slot indices: `:id` sits at slot 0 for **all 619 types**,
so even a single-class thunk could have guarded on the class alone and read
`values[0]`. The lookup advantage of explicit fields is therefore one saved array
indirection, not a change in guard breadth.

**This was adopted in full** — see "The four map classes" above. The record below
is kept because the resource half was adopted for reasons *other* than the ones
originally argued, and that distinction matters if it is ever revisited.

The backbone half pays for itself outright: `AbstractBackboneElement` and
`AbstractElement` fit those 473 types exactly, reuse tested code, and save 14% of
their memory.

The resource half does not pay for itself on the numbers, and was adopted for
uniformity instead. Two of the three original objections still stand:

1. **It does not enable `Base[]`.** The blocker is repeating properties, not
   strings: they hold `PersistentVector`, which cannot implement `Base`. Patient
   has 12 repeating properties out of 24. Making `id` a typed field removes the
   `String` from the array but leaves every list in it, so the array stays
   `Object[]`. `Base[]` only becomes reachable if lists are wrapped, which is
   rejected on memory grounds above.
2. **Memory gains nothing for resources.** Measured over the KDS bundle,
   explicit leading fields on `DomainResource` and `Resource` come out at exactly
   **±0** — moving a reference from an array slot to an object field costs the
   same 4 bytes either way. The measured −1,184 bytes of the full-hierarchy
   variant came entirely from the backbone half, which is now adopted by other
   means.
3. **It complicates the most compatibility-critical method.** The lexical hash
   order interleaves field-held and array-held keys. For Patient the sorted order
   runs active, address, birthDate, communication, contact, *contained*,
   deceased, *extension*, gender, generalPractitioner, *id*, identifier,
   *implicitRules*, *language*, link, … — italicised entries would live in
   fields. `hashInto` would need a discriminated index encoding (negative for
   field ordinals, non-negative for array slots) rather than a plain `int[]`, and
   `valAt`, `assoc`, `seq`, `entrySet`, `kvreduce` and `count` would each need
   field-versus-array dispatch. Serialization is unaffected, since element order
   puts the leading block first.

So explicit leading fields on the resource classes buy no measurable memory. They
were adopted anyway because the discriminated hash order is not an *extra* cost
once `ElementMap` and `BackboneElementMap` already require it: with all four
classes built alike, there is one encoding rather than two code paths, and every
`ILookupThunk` guard covers a whole abstract branch. Uniformity, not footprint.

Revisit the `Base[]` question only if lists are ever wrapped, at which point
`Base[]` and real type safety become reachable together.

### Why `ExtensionData` for backbone elements but not resources

`id`, `extension` and `modifierExtension` are ordinary element definitions for
every type, so they would otherwise be ordinary slots. Verified 2026-08-02
against the writing context:

```
:fhir/Patient  P = 24
  [:id StringPropertyHandler] [:meta ...] [:implicitRules ...] [:language ...]
  [:text ...] [:contained ...] [:extension ComplexListPropertyHandler]
  [:modifierExtension ComplexListPropertyHandler] [:identifier ...] ...

:fhir.Observation/component  P = 8
  [:id StringPropertyHandler] [:extension ComplexListPropertyHandler]
  [:modifierExtension ComplexListPropertyHandler] [:code ...] [:value ...] ...
```

The difference is position. `AbstractElement.serializeJsonBase` writes `id` then
`extension` at the *start* of the object, and `AbstractBackboneElement` appends
`modifierExtension`.

- For **backbone elements** that is exactly right — the order really is `id,
  extension, modifierExtension`, for all 464 of them. So `BackboneElementMap`
  extends `AbstractBackboneElement` and inherits `ExtensionData` (which also
  carries the `IObj` meta), the `modifierExtension` field, `serializeJsonBase`,
  `valAt` for `id`/`extension`, `references()`, and
  `MODIFIER_EXTENSION_LOOKUP_THUNK`. Three properties leave the values array.
  Measured saving: −5,136 bytes, −14.0%, with only 4 of 506 instances needing a
  real `ExtensionData` rather than the interned `EMPTY` singleton.
- For **resources** it is wrong — the order is `id, meta, implicitRules,
  language, text, contained, extension, modifierExtension`, so `extension` is
  seventh. Reusing `serializeJsonBase` would emit it second and violate the
  byte-identical-output invariant. Splitting the leading properties between
  `ExtensionData` and the array to fix the order would interleave two storage
  locations for no measured gain (see "Considered and partly adopted"), so
  `ResourceMap` keeps everything in the array.

The cost, paid only by `BackboneElementMap`, is that `hashInto` needs a
discriminated order rather than a plain `int[]`, and `valAt`, `assoc`, `seq`,
`count`, `entrySet` and `kvreduce` need a branch for the three inherited keys.
That is worth 14% of memory on the largest population of values; it would be
worth nothing on resources.

Neither class may extend `AbstractElement` *as a resource*: a FHIR resource is
not an `Element`, and the hierarchy is `Base → Element → AbstractElement`. That
constraint is what makes the split necessary rather than merely convenient.

In both classes `values` is `Object[]`, not `Base[]`: repeating elements are
`PersistentVector` and `System.String` properties are `java.lang.String`, neither
of which can implement `Base` (see "The two non-Base leaves" below).

There are 658 FHIR types represented as maps (185 top-level, 473 nested backbone
elements). They are distinguished by their metadata instance, not by class.

### `TypeMetadata`

Built once per type when the writing context is built, from the
StructureDefinition snapshot. It holds:

- the FHIR type keyword (e.g. `:fhir/Observation`), which also serves
  `valAt(:fhir/type)` — so `:fhir/type` costs nothing per instance
- `PropertyHandler[]` in element-definition order — this is the existing array
  from [writing_context.clj:107](../modules/fhir-structure/src/blaze/fhir/writing_context.clj:107),
  carrying each slot's `FieldName`, cardinality, and how to write its value
- `PropertyIndex` — the existing open-addressed keyword→slot index from
  [PropertyIndex.java](../modules/fhir-structure/java/blaze/fhir/writing/PropertyIndex.java),
  promoted from write-time scratch to the map's lookup index
- **two slot orders**, both `int[]`, both precomputed at init:
  - *element order* — for serialization (identity, `0..P-1`)
  - *lexical key order* — for hashing (see "Hash compatibility")

The two-orders property is the crux of the design. It gives canonical JSON output
and byte-identical content hashes without sorting anything at runtime.

### Serialization

`serializeAsJsonValue` is today's `writeProperties` loop with the fill step
deleted, because the values array *is* the slots:

```java
generator.writeStartObject();
for (int i = 0; i < values.length; i++) {
    Object v = values[i];
    if (v != null) metadata.propertyHandler(i).writeValue(generator, v);
}
generator.writeEndObject();
```

Resources additionally write `resourceType` first, as `ResourceTypeHandler` does
today. Whether that is a metadata flag or a subclass is an implementation
detail; a flag is preferred, to keep one class.

CBOR is unaffected and gets the same benefit: `write-cbor`
([resource.clj:1259](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:1259))
is `write-json` with a `CBORFactory`, calling the same code. XML is a separate
mechanism (spec2 `unform-xml`, `blaze.fhir.spec.impl`) and is untouched.

### Hash compatibility — the critical property

**Content hashes must not change.** They are stored, not recomputed, and they key
the content-addressed resource store. Changing them is possible but costly; this
design avoids it entirely.

Today, resources are plain maps, so `Base.hashInto` dispatches to `hashIntoMap`
([Base.java:175-199](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:175)):
it writes `HASH_MARKER_MAP` (37), collects the entries with `Keyword` keys into
an `ArrayList`, **sorts them by key**, then for each writes
`putInt(key.hasheq())` followed by the value.

The `*Map` classes implement `Base`, so `Base.hashInto`'s `case Base b` arm wins and
`hashIntoMap` is bypassed. `hashInto` must therefore reproduce that
byte stream exactly. It can:

> The sorted order of a subset is a subsequence of the sorted order of the full
> set. The key set of a FHIR type is fixed by its StructureDefinition. So
> precomputing one lexical key order per type and iterating it while skipping
> null slots yields exactly the sorted order of the present keys.

The precomputed order must be **discriminated** in all four classes, because the
leading properties live in fields rather than the array and interleave with
array-held keys in lexical order. For `Observation.component` the sorted sequence
is code, dataAbsentReason, *extension*, *id*, interpretation, *modifierExtension*,
referenceRange, value; for Patient it is active, address, birthDate,
communication, contact, *contained*, deceased, *extension*, gender,
generalPractitioner, *id*, identifier, *implicitRules*, *language*, link, … —
italicised entries being field-held.

Encode field references as negative values (−1 `id`, −2 `extension`, −3
`modifierExtension`, and for resources −4 `meta`, −5 `implicitRules`, −6
`language`, −7 `text`, −8 `contained`) and array slots as non-negative, then
switch in the hash loop. One encoding, used by all four classes.

This turns a per-map `ArrayList` allocation plus a comparison sort
(`Keyword.compareTo` → `Symbol.compareTo` → `String.compareTo`) into a flat loop
over an `int[]` with no allocation and no comparisons. Hashing runs on every
create and every PUT, so this is a write-path speedup as well as a compatibility
guarantee.

Three details must be preserved:

1. **`:fhir/type` participates.** It is a real entry today (keyword key, keyword
   value), hashed as `putInt(key.hasheq())` then `putInt(value.hasheq())`. Even
   though a `*Map` value serves it from metadata, `hashInto` must emit it at its
   lexical position. It sorts last, because `Symbol.compareTo` orders unqualified
   names before qualified ones and it is the only namespaced key — but include it
   as a pseudo-slot in the precomputed order rather than relying on that.
2. **`java.lang.String` values keep their quirk.** `hashIntoMap` hashes
   `System.String` values with the FHIR-String signature, deliberately
   ([Base.java:191](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:191)).
   The `*Map` classes are in the same package and must call the same method.
3. **Null-valued keys — the one accepted divergence.** `hashIntoMap` emits the
   key and then no value bytes for a present-but-nil entry, whereas a null slot
   in a `*Map` value means absent. For **parsed** resources this can never differ:
   [resource.clj:760](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:760)
   returns the accumulator unchanged for `VALUE_NULL`, so the key is never
   added, and present-key is equivalent to non-nil-value. Hand-written maps can
   still contain `{:id nil}`, and those will hash differently than they do
   today. That is accepted deliberately (decision 6) but must be verified to be
   unreachable in practice before implementation — see the corpus check in
   "Compatibility constraints".

### `memSize`

`Base.memSize`'s switch falls through to `default -> 0`
([Base.java:218-228](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:218)).
A map type that isn't handled reports zero bytes, the Caffeine weigher accepts
it, and **the resource cache grows without bound** — no exception, no failing
test, an OOM much later. Implementing `Base` gets the `case Base b ->
b.memSize()` arm, so this is safe by construction *provided* each `*Map` class
implements `Base` and its `memSize()` is correct:

```
aligned(MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE)   // the map object
  + memSizeObjectArray(values.length)                      // the values array
  + sum of Base.memSize(v) for non-null v                  // the leaves
```

The metadata instance is shared and must **not** be counted per instance.

### Map protocol semantics

| Operation | Behaviour |
| --- | --- |
| `valAt(k)` | `PropertyIndex` probe → `values[i]`; `:fhir/type` from metadata; miss → `notFound` |
| `assoc(k, v)` | copy `values`, set slot. `v == null` clears the slot. Unknown key → return `this`, matching `Coding.assoc` ([Coding.java:208](../modules/fhir-structure/java/blaze/fhir/spec/type/Coding.java:208)) |
| `without(k)` | inherited from `Base`, which defines it as `assoc(key, null)` ([Base.java:332-335](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:332)) — no separate implementation |
| `count` | number of non-null slots, +1 for `:fhir/type` |
| `seq` / `iterator` / `entrySet` | non-null slots in element order, plus `:fhir/type` |
| `kvreduce` | non-null slots in element order |
| `empty` | a `*Map` value with the same metadata and an empty values array |
| `equiv` / `hasheq` | `APersistentMap` semantics, so equality with plain maps still holds |
| `meta` / `withMeta` | required — `IObj`. `blaze.db.node/enhance-resource` attaches `:blaze.resource/hash`, `:blaze.db/tx` etc. via `with-meta` ([node.clj:186-198](../modules/db/src/blaze/db/node.clj:186)) |
| `invoke(k)` | required — `IFn`. `blaze.fhir.util` sorts with `#(% :id)` ([util.clj:118](../modules/fhir-structure/src/blaze/fhir/util.clj:118)) |
| `containsKey` | required — `(contains? resource :id)` at [util.clj:565](../modules/rest-util/src/blaze/handler/fhir/util.clj:565) |

`entrySet` must include `:fhir/type`: `Base.referencesMap` tests
`m.get(FHIR_TYPE_KEY) == FHIR_TYPE_BUNDLE_ENTRY`
([Base.java:210-216](../modules/fhir-structure/java/blaze/fhir/spec/type/Base.java:210)).

The `*Map` classes must implement `IKVReduce` directly. `Base` does not, so the
existing complex types reach `reduce-kv` through `clojure.core.protocols`'
seq-based slow path for `IPersistentMap`; the whole point here is the fast path.

Two call sites walk resources generically. Only one of them is a question:

- **`blaze.interaction.transaction.bundle.links` — already proven, no work
  needed.** It does `reduce-kv` plus `assoc` at arbitrary depth over transaction
  bundles ([links.clj:51-59](../modules/interaction/src/blaze/interaction/transaction/bundle/links.clj:51)),
  which looks like the most representation-dependent function in the codebase.
  It is safe by construction: every key it `assoc`es came from `reduce-kv` over
  that same map, so an unknown key cannot arise; it seeds the accumulator with
  the map itself, so the result stays a `*Map` value; and it skips `:fhir/type`
  explicitly at line 54, which is what a synthetic type key requires. Crucially,
  this walk **already** operates on self-serializing types — line 33 does
  `(update value :reference ...)` on a `Reference`, and line 43 recurses into
  any `:fhir/type`-bearing value, i.e. `Coding`, `Meta`, `Identifier` and the
  rest. It has done so since those types moved to Java.
- **`clojure.core/select-keys` cannot be used at all.** It is `(loop [ret {} ...])`
  — it starts from a literal empty plain map and `conj`es entries in, so its
  result is always a `PersistentArrayMap`/`PersistentHashMap` regardless of the
  input type. A plain map then hashes differently and cannot serialize itself.
  It must be replaced (see below).

### Replacing `select-keys`

`clojure.core/select-keys` is unusable because it seeds its accumulator with a
literal `{}`. The replacement seeds it with `(empty m)` instead, which is
type-preserving throughout this data model — `Coding.empty()` returns
`Coding.EMPTY` ([Coding.java:189](../modules/fhir-structure/java/blaze/fhir/spec/type/Coding.java:189)),
and `empty()` returns an empty value of the same class with the same metadata.

```clojure
(defn select-keys
  "Like `clojure.core/select-keys` but preserves the type of `m`."
  [m ks]
  (-> (reduce (fn [r k] (if-some [v (get m k)] (assoc r k v) r)) (empty m) ks)
      (with-meta (meta m))))
```

Three properties matter:

- **It works for plain maps too**, since `(empty {...})` is `{}`. That makes the
  migration incremental — call sites can switch before the values they operate
  on become `*Map` values.
- **A partial `*Map` value is a fully valid one.** The metadata is keyed
  by *type*, not by key set, so a subsetted resource still serializes and hashes
  correctly, skipping null slots exactly as a fully populated one does. There is
  no such thing as "metadata for a partial map" to invent.
- **The `with-meta` is required, not decorative.** `clojure.core/select-keys`
  ends with `(with-meta ret (meta map))`, and `_elements` subsetting runs on
  resources that carry `:blaze.db/tx` and `::sp/match-extension` in their
  metadata. Dropping it would silently break `_score` in search bundles and the
  transaction info in history bundles. `(empty m)` returns a shared singleton for
  the Java types, so metadata cannot be inherited implicitly.

`:fhir/type` need not be listed in `ks` for a `*Map` value — it comes from the
metadata — but existing call sites pass it anyway, which is harmless and keeps
them correct for plain maps during migration.

Call sites to convert (`select-keys` on FHIR values; the other uses in the
codebase are on config, query params and JSON and are unaffected):

| Site | Use |
| --- | --- |
| [node.clj:337](../modules/db/src/blaze/db/node.clj:337) | `_elements` subsetting, then `(update :meta update :tag conj-vec fu/subsetted)` |
| [capabilities_handler.clj:340-341](../modules/rest-api/src/blaze/rest_api/capabilities_handler.clj:340) | `_elements` on the CapabilityStatement |
| [expand.clj:76](../modules/terminology-service-local/src/blaze/terminology_service/local/value_set/expand.clj:76) | selects `:parameter`/`:contains` from `ValueSet.expansion`, then `(merge-with into)` — check that the merge also preserves the type |

### The two non-Base leaves

**Lists stay `PersistentVector`. Do not wrap them.**

Cardinality is static per slot, from the StructureDefinition, and the metadata
carries it. `Complex.serializeJsonComplexList` and
`Primitive.serializeJsonPrimitiveList` already exist as statics and do exactly
this job. The primitive one *must* pre-scan every element for `hasValue` and
`isExtended` before writing anything, because a repeating extended primitive
emits two parallel arrays (`"given"` and `"_given"`) that must stay
index-aligned; that logic belongs in the slot descriptor that knows the
`FieldName`, not in a wrapper that does not. `Base.hashInto` already handles
`case List<?>` and `Base.memSize` handles `case IPersistentVector`. A wrapper
would add a header plus a reference — 16 bytes — per list instance across a
FHIR-shaped tree, eating into the 57% saving for no benefit.

**`java.lang.String` cannot implement `Base`.** FHIRPath `System.String` is a
plain Java string — that is what `StringPropertyHandler` exists for
([writing_context.clj:91-92](../modules/fhir-structure/src/blaze/fhir/writing_context.clj:91)),
covering `Element.id` and `Extension.url`. `Base.hashInto` and `Base.memSize`
both have explicit `case String` arms. The slot descriptor must know this kind.

### Polymorphic properties

`PolymorphicPropertyHandler` cannot be dissolved into value dispatch.
`Patient.deceased[x]` serializes as `deceasedBoolean`, and
`ExtensionValue.fieldNameExtensionValue()` only ever returns the `valueX` form.
The per-slot type→`FieldName` table stays in the metadata.

### `#fhir/map` reader

One data reader tag. The type stays in the map:

```clojure
#fhir/map{:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}
```

`data_readers.clj`
([modules/fhir-structure/resources/data_readers.clj](../modules/fhir-structure/resources/data_readers.clj))
gains **one** entry, not 658. It currently has 62 FHIR tags, all hand-maintained;
no build-time codegen is needed.

The reader needs the per-type metadata, and data readers are static functions
with no injected context. A global registry is acceptable here and has direct
precedent: `blaze.fhir.structure-definition-repo` is a private `def` reading
fixed R4 bundles off the classpath, its `ig/init-key` **ignores its config
entirely**, and `register-all!` already populates the global spec2 registry from
it ([structure_definition_repo.clj:152-197](../modules/fhir-structure/src/blaze/fhir/structure_definition_repo.clj:152)).
There is no deployment variance in the type set.

`print-method` must round-trip: emit `#fhir/map{...}`, mirroring
`def-print-method-complex` in
[type.clj:386-395](../modules/fhir-structure/src/blaze/fhir/spec/type.clj:386).

**Migration.** Resources are written today as plain maps with a `:fhir/type` key
at 4800 sites across `.clj` files — 204 in `src`, 4596 in tests (counted
2026-08-02 with `grep -rho ':fhir/type :fhir[./][A-Za-z.]*' --include='*.clj'`).
Because the tag carries no type information, `{:fhir/type :fhir/Patient ...}` →
`#fhir/map{:fhir/type :fhir/Patient ...}` is a purely textual insertion and can
be scripted. Per-type literals would have required deleting the `:fhir/type` key
at every site, which is why the one-tag choice matters well beyond aesthetics.

### Parser integration

**The parser builds `*Map` values directly.**

The parser already knows the element each field belongs to — it binary-searches a
sorted field-name array at
[resource.clj:951-970](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:951)
— but discards that knowledge, appending to a mutable `ArrayList` in *document*
order and calling `RT/mapUniqueKeys` at the end
([resource.clj:549-559](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:549)).

Instead, store into the slot at the element's index. This removes the
`ArrayList`, the `.indexOf` linear scans, `.toArray`, and the map construction
from the read path, and the value arrives in canonical order for free.

The accumulator is a **local** `Object[]`, handed to the constructor only once it
is fully populated. Constructing the `*Map` value first and then writing slots
into its array would break safe publication — see "Immutability and safe
publication". This is not optional, and it is easy to get wrong precisely because
the single-threaded tests would pass either way.

One complication: `assoc-primitive-value`
([resource.clj:561-575](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:561))
exists because a property can be touched twice — `code` and `_code` for extended
primitives. Slot storage handles this naturally (read the slot, merge, write it
back) and is strictly simpler than the current `put-value!` scan.

`complex-type-finalizer`
([resource.clj:976-1018](../modules/fhir-structure/src/blaze/fhir/spec/resource.clj:976))
keeps its hand-written `condp` for the ~40 Java complex types; only its default
branch changes, from `persist-map` to constructing a `*Map` value.

The parsing context and writing context are separate components today, keyed
differently (`:Patient` vs `:fhir/Patient`). Both are derived from the same
StructureDefinition repo. The metadata instances must be shared between them, or
the parser will build maps whose metadata is not identical to the writer's.
Building the metadata registry once and having both contexts reference it is the
intended shape.

## What is deleted, what survives

Deleted:

- `AbstractMapTypeHandler`, `MapTypeHandler`, `ResourceTypeHandler`
- `SlotFiller`
- `ResourcePropertyHandler` — a contained resource is a `Complex`; call
  `serializeAsJsonValue`
- the `ArrayList` accumulator machinery in the parser (`get-value`,
  `put-value!`, `persist-map`, `persist-array-map`)

Survives, relocated into `TypeMetadata`:

- `PropertyHandler` and its remaining subclasses (`PrimitivePropertyHandler`,
  `StringPropertyHandler`, `ComplexPropertyHandler`, `ComplexListPropertyHandler`,
  `MapPropertyHandler`, `MapListPropertyHandler`, `PolymorphicPropertyHandler`) —
  these *are* "the field name and how to serialize the value", i.e. exactly what
  the metadata holds. Only `ResourcePropertyHandler` is deleted, because a
  contained resource becomes a `Complex` that serializes itself.
- `PropertyIndex` — becomes the map's lookup index
- `ComplexTypeHandler` and the ~40 hand-written Java complex types, unchanged

Net: the type-handler layer disappears; the property-handler layer is retained as
shape metadata and stops being invoked through a slot-filling indirection.

## Decisions

1. **One class, one tag.** 658 metadata instances, not 658 classes or readers.
2. **The parser builds `*Map` values directly.** The read path is where the time
   is (3071 µs vs 247 µs).
3. **Hashes stay byte-identical for every parsed resource.** Precomputed lexical
   order in the metadata. No version bumps, no duplicate rows in the
   content-addressed store, no dead search-index entries, no mixed-version
   divergence during rolling upgrades. The single exception is a hand-built map
   carrying an explicit nil value, which decision 6 accepts and the corpus check
   in "Compatibility constraints" must show to be unreachable.
4. **Lists stay `PersistentVector`.** No wrapper type.
5. **`assoc` follows the existing `Complex` convention: unknown keys are
   silently ignored.** `Coding.assoc` ends in `return this`
   ([Coding.java:208](../modules/fhir-structure/java/blaze/fhir/spec/type/Coding.java:208)),
   and the `*Map` classes must not be the only map types in the data model that behave
   differently for the same operation. Strictness belongs on the *construction*
   path instead — see decision 8.
6. **Nil means absent, uniformly.** `assoc(k, nil)` clears the slot, and
   `Base.without` falls out of it for free since it is defined as
   `assoc(key, null)`. A null slot is indistinguishable from a key that was
   never set.

   This is *more* consistent than the status quo. Today a plain map carrying
   `{:id nil}` serializes identically to one without the key — `writeProperties`
   skips null slots — but hashes differently, because `hashIntoMap` emits the
   key with no value bytes. Two maps with byte-identical JSON can therefore have
   different content hashes. The `*Map` classes remove that discrepancy.

   The cost is a hash divergence for any resource that today carries an explicit
   nil *and* gets hashed. See "Compatibility constraints" for the check that
   must run before implementation.
7. **Four classes, one per FHIR abstract type**: `ElementMap` (9),
   `BackboneElementMap` (464), `ResourceMap` (3) and `DomainResourceMap extends
   ResourceMap` (143). Each holds its abstract type's properties as explicit
   typed fields and the rest in the array.

   `BackboneElementMap` and `ElementMap` extend the existing
   `AbstractBackboneElement` and `AbstractElement`, reusing `ExtensionData`,
   `serializeJsonBase`, `valAt` for `id`/`extension`, `references()` and the
   lookup thunks — worth **−14% memory** on the backbone population, since their
   element order is exactly what `serializeJsonBase` emits. `ElementMap` may not
   share `BackboneElementMap`: inheriting an always-empty `modifierExtension`
   would make `valAt` and `contains?` diverge from a plain map.

   The resource classes cannot extend `AbstractElement` — a resource is not an
   `Element`, and its `extension` sits seventh, not second. Splitting them at
   `Resource`/`DomainResource` is **memory-neutral** (measured at exactly ±0; see
   "Considered and partly adopted"), and is chosen for structural uniformity and
   typed field access rather than footprint: with all four classes built the same
   way, one discriminated hash-order encoding serves all of them, and each
   `ILookupThunk` guard covers a whole abstract branch.
8. **Construction is strict about unknown keys.** The `#fhir/map` reader and the
   parser reject keys with no slot, rather than dropping them the way
   `Coding/create` does
   ([Coding.java:125-128](../modules/fhir-structure/java/blaze/fhir/spec/type/Coding.java:125)).
   Silently dropping is tolerable for a five-field `Coding` literal; for a
   resource it means a typo'd key vanishes *and* the hash silently differs from
   what the same map hashes to today. The parser already defaults
   `fail-on-unknown-property` to true, so this only aligns the literal with
   existing parser behaviour.

## Compatibility constraints

These are invariants, not goals. Each has a test that must exist before the
corresponding code.

- `hash/generate` returns the same bytes for a `*Map` value as for the
  equivalent plain map, for every resource in the KDS bundle.
- **Explicit-nil corpus check, before implementation.** Because nil now means
  absent (decision 6), any resource that is hashed while carrying an explicit
  nil value would hash differently than it does today. Hashing happens only on
  the write path — `hash/generate` is called at
  [transaction.clj:15,37](../modules/db/src/blaze/db/node/transaction.clj:15) and
  [interaction/util.clj:32,41](../modules/interaction/src/blaze/interaction/util.clj:32)
  — and parsed resources cannot contain nils, so the exposure is limited to
  resources built by hand in production code (job Tasks, MeasureReports,
  OperationOutcomes, CapabilityStatement). Confirm none of them can produce a
  nil-valued key, e.g. via `{:fhir/type :fhir/X :id id}` where `id` is nil. If
  any can, fix the construction site rather than weakening the design.
- `write-json` and `write-cbor` produce byte-identical output to today.
- `Base/memSize` returns a non-zero, correct value for every `*Map` class.
- Resources remain usable as Clojure maps by every consumer: keyword lookup,
  destructuring, `assoc`/`update`/`dissoc`, `reduce-kv`, `contains?`, `with-meta`,
  `(resource :id)` function invocation.
- Every field is `final` and the `values` array never escapes or is mutated after
  construction. The parser populates a local array before calling the
  constructor, so the final-field freeze covers the array contents. Instances are
  shared across threads via the resource cache, and a violation here produces a
  race no single-threaded test can detect.

## Open questions

- **Interning.** The Java complex types intern aggressively (`Coding.maybeIntern`).
  Whether the `*Map` classes should intern is unexplored and probably out of scope for
  the first pass.

## Build order

Test-driven, per `AGENTS.md`. Each step must be green before the next.

1. **Hash stability harness first.** Before any production code: a test that
   parses resources, builds both the plain-map and `*Map` representations,
   and asserts `hash/generate` agrees. Everything else depends on this property,
   so it must be able to fail before it can pass.
2. `TypeMetadata`: element order, lexical key order, `PropertyIndex`, per-slot
   descriptors. Unit-test the two orders directly.
3. `*Map` map protocol: `valAt`, `count`, `seq`, `entrySet`, `kvreduce`,
   `assoc`, `dissoc`, `equiv`, `hasheq`, `meta`/`withMeta`, `invoke`,
   `containsKey`, and `getLookupThunk` for **every** property. Property-based
   tests against an equivalent plain map.

   The thunks need their own tests, for two different failure modes. A guard that
   is too *narrow* degrades silently to `valAt` — slow but correct — so assert
   that each thunk returns the value for an in-shape target. A guard that is too
   *wide* returns the wrong property, silently and without error, so assert that
   a thunk built for one type returns itself (not a value) when handed a value of
   another type sharing the same Java class. Push a `Patient` through a thunk
   built from an `Observation`; it must not produce a value.
4. `hashInto` — turn on the step-1 harness.
5. `memSize` — assert non-zero and compare against JOL, in the style of the
   existing `^:mem-size` tests in `modules/fhir-structure/test/blaze/fhir/spec_test.clj`.
6. `serializeAsJsonValue` — assert byte-identical JSON and CBOR against the
   current writer for the whole KDS bundle.
7. Parser builds `*Map` values.
8. Type-preserving `select-keys`, and convert the three call sites. Can be done
   independently of everything above, since it works for plain maps too.
9. `#fhir/map` reader and `print-method`, then the scripted literal migration.
10. Delete the dead handlers.
11. Re-run the benchmarks in `spec_test_perf.clj` and the memory comparison.

## Verification

```
make fmt
make lint
make -C modules/fhir-structure test
make test-coverage
```

Coverage must stay ≥ 95% forms. After editing `.java` files, force a recompile —
`make test` uses stale `.class` files otherwise:

```
cd modules/fhir-structure && clojure -T:build compile
```

On a fresh worktree, run `make build-ig` first.

Benchmarks live in the `comment` block of
`modules/fhir-structure/test-perf/blaze/fhir/spec_test_perf.clj` and are run from
a REPL with the `:test-perf` alias. The relevant ones are
`(bench-write-json (read-json "Bundle" (slurp kds-bundle-filename)))` and
`(bench-read-json "Bundle" (slurp kds-bundle-filename))`.

## Key file reference

| Concern | Location |
| --- | --- |
| Current write handlers | `modules/fhir-structure/java/blaze/fhir/writing/` |
| Writing context construction | `modules/fhir-structure/src/blaze/fhir/writing_context.clj` |
| Parsing context construction | `modules/fhir-structure/src/blaze/fhir/parsing_context.clj` |
| Parser | `modules/fhir-structure/src/blaze/fhir/spec/resource.clj` |
| `Base` / hashing / memSize | `modules/fhir-structure/java/blaze/fhir/spec/type/Base.java` |
| Java complex types | `modules/fhir-structure/java/blaze/fhir/spec/type/` |
| Data readers | `modules/fhir-structure/resources/data_readers.clj` |
| Type constructors | `modules/fhir-structure/src/blaze/fhir/spec/type.clj` |
| Hash generation | `modules/fhir-structure/src/blaze/fhir/hash.clj` |
| No-op update detection | `modules/interaction/src/blaze/interaction/util.clj:29-53`, `modules/db/src/blaze/db/node/tx_indexer/verify.clj:141-197` |
| Resource cache weigher | `modules/db/src/blaze/db/resource_cache.clj:119-122` |
| Public read/write API | `modules/fhir-structure/src/blaze/fhir/spec.clj` |
