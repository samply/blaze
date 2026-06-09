# Blaze v2 — Core Storage Redesign

## Context

Today every Blaze index key (and every search-param index value) carries the FHIR **logical id** as a variable-length byte string up to 64 chars. A meaningful share of real deployments use SHA-256-hex (64 random chars) as the id. This is the worst case for RocksDB on three fronts simultaneously: maximum key size, no prefix locality (ids are uncorrelated), and degraded bloom-filter selectivity.    

The content-hash–keyed resource store was inherited from XTDB's immutable-document model. With conditional references and conditional updates (planned for v2), Blaze must rewrite resource bodies at write time, so the "content is immutable, deduplicates naturally" assumption no longer holds. The hash is random (bad for KV locality) and yields no realistic dedup across `(type, id)` pairs.

Blaze v2 introduces:

1. A fixed-width **internal resource id** that replaces the logical id inside every index key, and is used as the internal handle in the join/compartment/search plumbing.
2. A write-time **rewrite stage** that resolves bundle-internal placeholders, conditional references, and conditional updates before indexing — enabling these FHIR features that v1 lacks.
3. A unified **per-node RocksDB Index DB** that now also holds resource bodies, in a dedicated `Resource` column family keyed by `(internal-id, num-changes)`. The content hash is dropped as a key and the separate resource-store module goes away.
4. Removal of the Cassandra-backed resource store. State is per-node RocksDB everywhere; the **transaction log backend remains pluggable** (local in-memory and Kafka).
5. A **WAL-style transaction log** with bounded retention. The index is authoritative; the log is no longer a recovery mechanism. Standalone deployments use an in-memory log only. Bounded retention is also a hard requirement for GDPR compliance: Blaze processes personal data, data subjects have a right to erasure, and an unbounded immutable log would make true deletion impossible. `delete-history` / `patient-purge` remove version rows and bodies from the index; the log must age out the originating commands within the retention window for that deletion to be effective.
6. A **single inline-or-spill transport mechanism** for shipping commands+bodies from submitter to apply, with an optional S3-compatible `BlobStore` for overflow in Kafka deployments.

v2 is a clean break: no on-disk compatibility with v1, no v1 transaction log replay. Bootstrap (both v1 → v2 migration and late-joining v2 nodes that fell behind the tx-log retention window) goes through a single network **state sync** protocol implemented by both v1 and v2 servers. The v2 command vocabulary is free to differ from v1.

The storage layer is designed sharding-ready from the start. Every sharded CF carries a 2-byte `shard-id` prefix; `ResourceAsOf` / `TypeAsOf` / `SystemAsOf` values carry a 2-byte `shard-id` field recording each version's owner shard. In the standalone variant the `shard-id` is always `0x0000` (or `0xFFFF` for `:global-replicated`-class resources); in a sharded cluster it distributes per-version per the placement rules in [Patient Sharding](v2-patient-sharding.md). The same code path serves both, with `N=1` as the degenerate single-shard case. See [CF Placement Classes](#cf-placement-classes) below.

## Resource Type ID (tid)

The 4-byte murmur3 hash of the type name used today is replaced by a **2-byte densely-assigned type id**. The mapping `type-name ↔ tid` lives as a static table in code (`modules/db/src/blaze/db/impl/codec/tid.clj`), seeded with all R4 resource types in alphabetical order. New types introduced by R5, R6, etc. are appended to the table; existing entries are immutable and never reused. 2 bytes accommodates 65 535 types (R4 ships ~150).

Effect across the schema: every index key that today carries a `c-hash`-style 4-byte `tid` carries a 2-byte `tid` in v2. Hash-collision concerns disappear. Type-prefix scans get a denser, more compressible prefix. The `tid` function in `modules/db/src/blaze/db/impl/codec.clj` is rewritten as a table lookup; callers stay the same.

The same principle could be applied to the 4-byte `c-hash` of search parameter codes (which are also a finite, enumerable set per FHIR version). v2 keeps `c-hash` at 4 bytes for now — the search-parameter set is larger and less stable across IGs/extensions, and the win is smaller. Marked as a follow-up.

## Transaction Timestamp (t) Encoding

The logical transaction timestamp `t` — a monotonically increasing per-cluster counter assigned at **submission** (the Kafka record offset on the `blaze-tx` topic in the distributed variant; the equivalent in-memory submission counter in standalone) — uses **44 bits** of value range. In memory `t` is a Java `long`; on disk, wherever `t` or `t-desc` appears as a key or value field, it is stored byte-aligned as **6 bytes** (big-endian, unsigned). v1 used 8 bytes. `t` is assigned to every submitted transaction, including those that later land in `TxError`; it is not contingent on successful apply.

**Range.** 2^44 ≈ 1.76 × 10^13 transactions. Sustained-rate horizons:

| Sustained rate                                                                   | Horizon      |
|----------------------------------------------------------------------------------|--------------|
| 100 tx/s (typical today)                                                         | ~5 580 years |
| 1 000 tx/s (today's serial-apply ceiling)                                        | ~558 years   |
| 10 000 tx/s (realistic per-Kafka-partition OLTP ceiling for FHIR-sized payloads) | ~56 years    |

44 bits comfortably covers any plausible Blaze deployment — including a future OLTP-style operating mode in which the apply loop is parallelised and submission throughput rises by an order of magnitude — without a second schema upgrade. The 4 bits between 44 and the byte-aligned 48 are unused on disk; in memory they are simply the high bits of the `long`, always zero.

**Reach across the schema.** Every key or value that v1 carries as 8-byte `t` or `t-desc` narrows by 2 bytes in v2:

- `ResourceAsOf`, `TypeAsOf`, `SystemAsOf` keys (`t-desc` component) and their `purged-at?` value field.
- `PatientLastChange` key (`t-desc` component).
- `TxSuccess`, `TxError` keys (sole `t` component).
- `TByInstant` value (`t`).

For history- and time-ordered indices that every `_history`, `vread`, and CQL-cohort path reads, this is a 2-byte saving on every entry — meaningful at the volumes those CFs reach.

**Coupling to the internal id.** The same 44-bit `t` is bit-packed into the high bits of the 8-byte internal id (see *Internal ID Format*) so intra-transaction locality is preserved and the internal id never needs to widen beyond a `long`. The internal id itself is treated as opaque elsewhere: no code path decodes `t` out of an id at read or query time. The authoritative `t` for any row is the row's own `t` field; the embedding inside the id is a locality and uniqueness mechanism, not a public encoding.

**Overflow.** Approaching 2^44 is a non-event in practice, but the apply loop checks for it: a transaction whose assigned `t` would overflow fails with an `:cognitect.anomalies/conflict` anomaly recorded in `TxError`. No silent wraparound. The error is terminal.

## Internal ID Format

8 bytes — a Java `long` in memory, big-endian on disk. The 64 bits are partitioned at allocation time as:

- **High 44 bits — `t`**: the transaction timestamp at which the id was **first allocated**, same value-space as defined under *Transaction Timestamp (t) Encoding*. Placing `t` in the high bits makes the long's natural numeric order coincide with `(t, counter)` order, which is what every internal-id-keyed CF needs for intra-transaction locality.
- **Low 20 bits — counter**: per-transaction allocation counter starting at 0 within each transaction. 2^20 ≈ 1.05 M allocations per transaction — around 15× the largest practical FHIR bundle and well above any operational bulk-import size.

The bit layout is an internal property of how new ids are minted; **the id is treated as opaque thereafter**. No code path decodes `t` or the counter out of an internal id at read or query time. The authoritative `t` for any version is the `t` field of the relevant `*AsOf` row; the embedding inside the id only ensures uniqueness and locality.

**Properties:**

- **Globally unique** across all resource types — the type is not encoded into the id; types are carried separately as `tid` where type-prefixed scans matter.
- **Stable for the lifetime of a resource** — assigned once, never reassigned, never reused. All versions of a resource share the same internal id.
- **Locality** — resources written in the same transaction are contiguous in internal-id order; successive transactions are contiguous in turn. This is near-ideal for RocksDB block compression, bloom filters, and bulk-load scans. The locality comes from the bit layout but the layout is never decoded at runtime — only the resulting numeric order is observed.
- The high-bit `t` value inside an id encodes **first-sight** time, not creation time: for an id allocated as a phantom for an unresolved reference, the actual create transaction is later. Since the id is opaque thereafter, this is never read; creation time is read from `ResourceAsOf`.

## Logical ↔ Internal Mapping (Allocate-on-First-Sight)

Two new column families, written as a symmetric pair in a single `WriteBatch` at allocation time:

| CF                      | Key                               | Value                              |
|-------------------------|-----------------------------------|------------------------------------|
| `LogicalIdToInternalId` | `tid (2), logical-id-bytes (≤64)` | `internal-id (8)`                  |
| `InternalIdToLogicalId` | `internal-id (8)`                 | `tid (2), logical-id-bytes (≤64)`  |

`InternalIdToLogicalId` is the authoritative mapping from an `internal-id` to its `(tid, logical-id)` — resource identity, not anything per-version. It is one entry per resource, written once at first allocation and never updated; identity is stable for the lifetime of the resource, same lifetime semantics as `LogicalIdToInternalId`. The CF is on every read path because field elision strips `id` (and `resourceType`) from the stored body — there is no fallback of "decode the body and read `.id`". It also covers cases where no body is available at all: delete tombstones never had one, and `delete-history` / `patient-purge` can remove every body-bearing version while leaving metadata rows that still need to render as `Type/id` in `_history`. `tid` is carried in the value so that callers holding only an `internal-id` — chiefly reference rendering during body materialization, where every target's `(type, logical-id)` is needed — can resolve to `(tid, logical-id)` in a single point read, without a `ResourceAsOf` seek whose `num-changes` / `op` / `purged-at?` would be discarded.

Cost: roughly the same order of magnitude as `LogicalIdToInternalId` itself — on the order of ~25 GB on disk for 1 B resources after compression for SHA-256-hex id deployments, materially less for shorter ids. Easily covered by the v2 shrinkage of `ResourceAsOf` and the search-param indices.

### Canonical Allocation Order

To guarantee identical internal-id assignment across nodes without exchanging messages or relying on a refs list in the tx command, allocation is **sort-based**, not walk-based. Traversal of parsed bodies can use any order (including Clojure map iteration, which is not portable across JVMs and not stable across `PersistentArrayMap`/`PersistentHashMap` representations); only the final allocation order needs to be canonical.

For each transaction, applying nodes execute:

1. **Process commands in tx-submitted order** to determine each command's `(tid, logical-id)` target. For `conditional-delete` and conditional updates, resolve the search against `db(t-1) ∪ {targets already determined for earlier commands in this transaction}`. Resolution outcomes are deterministic (set-membership, exactly-one), so every node arrives at the same target set.
2. **Run the rewrite stage** on each body in command order: bundle-internal placeholder substitution, then conditional reference resolution against the same augmented db view as step 1. Substitution mutates a value at a fixed location; traversal order is irrelevant.
3. **Collect the set of `(tid, logical-id)` pairs** that appear as either command targets or as the target of any FHIR `Reference` element inside any rewritten body. Traversal of the parsed body (Clojure data) may be in any order — we only collect a set.
4. **Sort newly-appearing pairs** (those not already in `LogicalIdToInternalId`) by raw `(tid-bytes, logical-id-bytes)` ascending. This sort is the canonical order; it depends only on byte content, not on any in-memory representation.
5. **Allocate** internal ids in sorted order, advancing the per-transaction counter from 0 upward, and write each new pair to both `LogicalIdToInternalId` and `InternalIdToLogicalId` together with the rest of the transaction's tx-index `WriteBatch` — committed before the final `TxSuccess` `WriteBatch` (see [Apply-Time Mechanics](../implementation/database.md#apply-time-mechanics)).

**Properties:**

- Every node sees the same set of new pairs (steps 1–3 are deterministic), sorts them by the same total order (step 4), and advances the same counter in the same order (step 5). Identical mapping on every node.
- The submitting node does **not** need to compute or carry a refs list. The v1 `:refs` field on commands is **removed** in v2 — references are derived from the rewritten body at apply time, and referential-integrity verification uses the same derived list.
- Mutual references inside the same transaction are well-defined because the full `(tid, logical-id)` set is collected before any allocation. Phantom ids (refs whose target doesn't exist) are allocated by the same mechanism — they're just pairs in the set.
- Locality is preserved within practical limits: all ids allocated by one transaction share the same high-44-bit `t` prefix (top 6 bytes of the internal-id long), so they cluster regardless of intra-transaction order.

### Allocation Invariants

- Both CFs are **append-only**: never garbage-collected, never invalidated by `delete`, `delete-history`, or `patient-purge`. A future re-create of the same logical id reuses the same internal id, keeping audit/provenance references consistent.
- **Phantom ids** (allocated for a reference whose target is absent under ref-integrity-off) are allocated during step 4 and never need a backfill; if the target resource is later created, the mapping already has its id.
- **Mutual references inside the same transaction** are well-defined because all command targets are pre-allocated in step 2 before any body walk begins.
- `InternalIdToLogicalId` is the **authoritative source** for the logical id of any allocated internal id. Callers (e.g. `_history` rendering of delete tombstones, CQL `Resource.id` access without body materialization, state-sync stream of all-purged resources) read it directly rather than relying on body presence.

### In-memory caches

Both mapping CFs sit behind a JVM-resident cache (Caffeine), one per direction. The CFs' properties make these caches unusually well-suited:

- **Heavy access skew.** Reference targets follow a power law: every `Observation` / `Encounter` / `Condition` / `MedicationRequest` resolves to the same handful of `Patient`s in scope; `medicationReference` resolves to a small set of `Medication` resources used thousands of times each; a small number of `Organization`s back most other things. The same skew applies in both directions — at apply time the rewrite stage looks up `(tid, logical-id) → internal-id` on every body reference; at read time materialization looks up `internal-id → (tid, logical-id)` on every body reference. A cache holding the popular 0.1–1% of resources absorbs the bulk of lookups in both cases.
- **Append-only, no invalidation.** Entries are immutable for the lifetime of the resource — load on miss, evict by size policy, never invalidate. No coordination with the apply loop is needed.
- **Tiny entries.** ~10 bytes payload per entry (`tid 2 + logical-id ≤ 64`, or 8 bytes the other way). A cache sized for 1M entries fits comfortably under 200 MB of heap and, given the access skew, typically delivers > 95% hit ratio.
- **Latency gap below the cache.** Even on a RocksDB block-cache hit, a lookup costs block-cache lock + binary search + JNI roundtrip + Java byte allocation + decode. A JVM cache holding the already-decoded value is a `ConcurrentHashMap` lookup — nanoseconds vs microseconds. For resources that materialize with many references (`Bundle`, `Composition`, `Encounter`), the per-resource fan-out makes this gap visible end-to-end.

Composition with the other caches:

- The **materialized-resource cache** absorbs hot resource reads end-to-end — no mapping-CF lookup happens at all on a hit.
- The `InternalIdToLogicalId` cache catches the skew on materialized-cache misses, on `_history` rendering (which doesn't sit behind the materialized cache because it spans many versions), and during CQL cohort materialization.
- The `LogicalIdToInternalId` cache catches the skew on the rewrite-stage write path: bundle-internal placeholder resolution, conditional reference resolution, and per-body reference translation all hit it.
- The RocksDB block cache shared across the metadata CFs (see *Block cache strategy*) backstops the long tail in both directions.

Each layer earns its keep on a distinct slice of the workload; together they leave very little hot work for the underlying SST blocks.

## Write-Time Rewrite Stage

A new stage `tx-indexer.rewrite`, invoked from `node/tx-indexer.clj` before `expand`/`verify`. Runs at apply time on every node. Inputs: the raw transaction commands as they came from the tx log. Outputs: a transformed transaction whose bodies refer only to resolved logical ids and whose internal-id plumbing is populated.

Steps, in order, on each command:

1. **Bundle-internal placeholders** (`urn:uuid:…` ↔ `fullUrl`). Substitute the assigned logical id of the same-bundle resource into the body. Today this lives in `interaction/transaction/bundle/links.clj`; in v2 it moves to the apply path so it is deterministic across nodes and survives tx-log replay.
2. **Conditional references** (e.g. body contains a reference of the form `Patient?identifier=…`). Execute the search against the db value at `t-1` augmented with resources being created in the same transaction. Must yield **exactly one** match; substitute the resolved logical reference (`Patient/foo`) into the body. Zero or >1 matches → `TxError` for the whole transaction.
3. **Conditional update targets** (`PUT Patient?identifier=…`). Resolve via the same mechanism. Zero matches → command becomes a `create` with a server-assigned logical id; one match → command becomes a `put` against that logical id; >1 → `TxError`.
4. **Version dedup.** For each `put`/`create` against an existing `(tid, logical-id)`, fetch the current version's body from the `Resource` CF and compare it with the rewritten body. If equal, the command is a no-op at the storage layer: no new version, no new search-param index entries, no `num-changes` bump. The transaction's `t` is still recorded in `TxSuccess`. This replaces the `keep` command entirely.
5. **Resolve references to internal ids**. After body rewrites, collect the canonical sort set of `(tid, logical-id)` pairs (see *Logical → Internal Mapping*), allocate any new internal ids, and translate references for index writes.

The submission protocol stays fire-and-forget. Submitter still writes commands to the tx log (or the in-process equivalent in standalone) without waiting on peers; the fan-out of one log → many node-local apply loops is preserved in the distributed storage variant. What changes is the **command shape**: v2 commands carry the inline body (or a blob ref for overflow) and may include conditional reference / conditional update clauses, but they no longer carry a precomputed content hash or `:refs` list. The v2 command vocabulary is defined fresh and is not constrained to be a superset of v1's.

Determinism: every step above is a pure function of the transaction's commands and the db value at `t-1`. Conditional ref resolution depends only on set membership (exactly-one), not on iteration order, so it cannot diverge across nodes. The order in which internal ids are minted is fixed by the canonical sort defined under *Logical → Internal Mapping*, which depends only on raw bytes — not on in-memory data-structure traversal.

## Transaction Log

The retention model changes fundamentally in v2: the **index is authoritative**, the log is WAL-style with bounded retention. Nodes that fall behind the retention window catch up via the network state sync protocol (see *Node Bootstrap*); index rebuilds otherwise use in-place index recalculation — **never** log replay.

Bodies travel through whatever transport the variant uses, alongside the commands that reference them. The two variants below describe both the tx-log semantics *and* the body shipping that runs through it; they are not separable concerns.

### Standalone variant

**Tx-log semantics:**

- **In-memory tx-log only.** No on-disk tx-log writes of any kind.
- **Durability comes from the index commit.** Apply is layered as in v1's three-layer structure (see [Apply-Time Mechanics](../implementation/database.md#apply-time-mechanics)), now with bodies joining the per-resource batches: parallel per-resource `WriteBatch`es covering the `Resource` CF, search-param, reference, compartment, and `PatientLastChange` CFs first; then a tx-index `WriteBatch` covering the `*AsOf` family, the logical↔internal mapping CFs, and the `*Stats` counters; then the final `TxSuccess` / `TByInstant` `WriteBatch` as the visibility gate. `LogicalIdToInternalId` / `InternalIdToLogicalId` are written together with the tx-index batch (allocation is atomic with the rest of the transaction's index writes, as described under *Logical ↔ Internal Mapping*); the in-memory allocator hands the new mappings to the per-resource indexers via memory so they don't need the mappings on disk yet. `d/transact` returns success only after the final batch commits. A crash before that point: no client got success → no data loss from the client's perspective; orphan per-resource rows are inert because reads gate on `head(TxSuccess)` and unreferenced rows are never reached. A crash after: the transaction is durable in the index.
- **In-memory structure** is a bounded ring buffer of recent transactions, sized for subscription consumers. Slow subscribers that fall behind the ring get a "resubscribe from state" signal and replay forward via `_since` against the indices.
- **No tx-log replay code path.** No tx-log CF on disk. No coordination between ingest and apply (same process, same writer).

**Body shipping (in-process pass-through):**

- There is no wire and no serialization at transport time. The in-memory tx-log holds command lists whose body slots carry the **parsed FHIR resource** (Clojure data) directly. Submitter hands the command list to the apply path inside the same process; the rewrite stage operates on the in-memory tree.
- CBOR encoding happens **only** at the storage boundary, when the rewritten body is written to the `Resource` CF. There is no inline/spill decision, no threshold, no `BlobStore`, no transport-size limit.

### Distributed storage variant

The current tx-log implementation in this variant is Kafka; the description below describes its semantics.

**Tx-log semantics:**

- **Single topic, single partition:** `blaze-tx`. Bodies travel inline or by `BlobStore` reference within each commands message.
- **Retention:** time-based, sized to cover worst-case node downtime / replication catch-up. Not a recovery mechanism.
- **Fan-out:** unchanged. One log → many node-local apply loops. Each consumer commits offset only after the transaction's final `TxSuccess` `WriteBatch` lands.

**Body shipping (inline + spill):** bodies must be serialized for the wire and may exceed `max.message.bytes` for large submissions. A single inline-or-spill algorithm decides which bodies travel inline in the commands message and which are uploaded to an optional `BlobStore` and referenced by hash.

**Body encoding on the wire.** FHIR does not specify a CBOR wire format; the transport encoding used here is `blaze.fhir.spec`'s internal CBOR, applied uniformly so that submitter and applying nodes agree on the byte form. The interaction layer has already parsed the incoming Bundle into Blaze's internal FHIR data model; at command construction the submitter CBOR-encodes each entry's parsed body once. Those bytes are what the inline/spill algorithm sizes, what travels inline in the Kafka commands message, what's uploaded to `BlobStore`, and what `sha256` is computed over for the blob reference. Applying nodes decode them back to parsed FHIR before handing the result to the rewrite stage. The transport `sha256` is a transport-only identity / integrity check and never appears in any RocksDB key.

**Submitter:**

1. Serialize each resource body to CBOR; record sizes `[s_1 … s_N]`.
2. Discover the effective transport size limit `L`: query `max.message.bytes` via Kafka `AdminClient.describeConfigs(ConfigResource(TOPIC, "blaze-tx"))`, intersect with producer `max.request.size`. Cache with TTL; refresh on `RecordTooLargeException`.
3. Compute `total_min = base_command_metadata + N * blob_ref_slot_size`. The blob-ref slot is ≈ 40 bytes (sha256 binary 32 bytes + small wrapper; the hash subsumes integrity verification, the size is recoverable from the fetched bytes).
4. If `total_min > L` → reject submission with FHIR `OperationOutcome` (`too-costly`, HTTP 413). Pathological N; not encountered in practice.
5. Otherwise, `headroom = L − total_min`. Sort bodies **ascending** by `s_i`. For each in order: if `inline_cost(s_i) = s_i + inline_wrapper − blob_ref_wrapper ≤ headroom`, inline it and decrement headroom; else mark for spill and stop (remaining bodies are all larger, none will fit). Sort-ascending greedy is optimal for "maximize inlined count under a byte budget."
6. If any body is marked for spill **and no `BlobStore` is configured** → reject submission. Otherwise upload spilled bodies to the blob store keyed by `sha256(CBOR-bytes)` (idempotent under retry / concurrent identical uploads).
7. Publish the commands message. Each command's body slot is either `{ inline: CBOR-bytes }` or `{ blob: sha256-32-bytes }`.

**Applying node:**

1. Read commands message from Kafka.
2. For body slots with blob refs, fetch from `BlobStore` in parallel; verify `sha256(bytes) == ref` on receipt. Hash mismatch → fail the transaction with a clear operator-actionable error.
3. Inline slots: CBOR-decode to parsed FHIR data.
4. Proceed with rewrite stage, internal-id allocation, and index commit.

**Notes:**

- No separate operator-facing "Blaze inline threshold" knob; the threshold is fully derived from Kafka transport config.
- Sha256 is used as a transport identifier only; it never appears in any RocksDB index key. The v2 "no content hash as storage key" rule is unaffected.
- The inline-cost form `s_i − const` preserves ordering vs. raw `s_i`, so sort-by-size is correct.

### BlobStore protocol

Distributed-storage-variant only.

- New protocol `BlobStore` with `put(hash, bytes)`, `get(hash) → bytes`, `delete(hash)`.
- One reference implementation in scope: **S3-compatible** (covers AWS S3, MinIO, GCS via S3 interop, Azure via S3 gateway). Keys = sha256 hex (64 chars). Random leading bytes give natural prefix distribution; no sharding scheme needed at the protocol level.
- Lifecycle: **transport-only.** Blobs are dead weight once every applying consumer has processed the referencing commands message. Default policy: time-based retention on the blob store, sized like Kafka log retention. A janitor that consults Kafka consumer-group offsets to actively delete applied blobs is a possible future optimization.
- **Configuration is optional.** Without a `BlobStore`, submissions whose CBOR size exceeds the effective transport limit are rejected upfront. Deployments with high `max.message.bytes` may never need one. The standalone variant never invokes a `BlobStore`.
- Filesystem `BlobStore` impl is not in scope.

**Failure modes:**

- **Blob store outage at apply time.** Apply loop blocks at the offending commands offset, retries with backoff. Operator-visible. Same severity as Kafka being down.
- **Sha256 mismatch on fetched blob** (transport corruption, deliberate tampering). Fail the transaction; surface to operator. Index never sees an unverified body.
- **Submitter crashes between blob upload and commands publish.** Orphan blobs sit in the blob store until time-based retention removes them. No node-side bookkeeping required.
- **Submission with overflow but no blob store configured.** Rejected at receive time with `OperationOutcome` (`too-costly`), before any partial work is performed.

### Common to both variants

- `TxSuccess`, `TxError`, `TByInstant` are **index CFs**, not log artifacts. They remain on disk and queryable in both variants.
- The `TxLog` and `Queue` protocols (`modules/db-tx-log/`) survive but the Kafka impl simplifies (single-topic) and the standalone impl becomes in-memory.

### Command and Message Format

The in-memory shape of a transaction — what the submitter hands to the apply path in standalone, and what gets serialized into one Kafka record in the distributed variant — is the same in both variants. Only the body slot type and the outer serialization differ.

**Transaction payload.** One transaction = one Kafka record. The record value is a CBOR-encoded list of command maps (shape below), in submission order. Transaction identity is the `t` derived from the Kafka offset (recorded in `TxSuccess` / `TxError`); the authoritative instant is the broker's `LogAppendTime` on the record (the topic must be configured with `message.timestamp.type=LogAppendTime`, as in v1); producer-retry dedup is handled by Kafka's idempotent producer.

**Schema version.** Carried as a Kafka record header `v` with a single-byte value (`0x02` for v2). The applier reads the header before dispatching to a deserializer; an unknown version fails the message into `TxError`. Keeping the version out of the payload avoids paying for it on every record and matches its role — it describes how to interpret the bytes, it isn't part of them. Standalone needs no version (in-process, code-coupled).

**Commands (v2 vocabulary).** One of: `create`, `put`, `delete`, `conditional-delete`, `conditional-update`, `delete-history`, `patient-purge`. The op is carried as the `op` field in every command map. The `type` field is the resource type name as a string, translated to `tid` at apply via the static type table (carried as the name so messages stay readable without the tid table loaded).

Negative space relative to v1: no `hash` (v1's content hash), no `refs` list, and no `keep` op. The hash is gone entirely (the resource store is keyed by `(internal-id, num-changes)`); references are walked from the rewritten body at apply; version dedup is decided at apply by byte-comparing the rewritten body against the current version (see *Rewrite Stage*, step 4). The rewrite stage is what makes deriving these at apply safe — see *Determinism* there.

#### Create

The `create` command is used to create a resource.

##### Properties

| Name          | Required | Data Type       | Description                                      |
|---------------|----------|-----------------|--------------------------------------------------|
| type          | yes      | string          | resource type                                    |
| bodyInline    | one-of   | bytes           | inline body bytes (see Body below)               |
| bodyBlob      | one-of   | sha256 (32 B)   | blob reference for spilled body (see Body below) |
| if-none-exist | no       | search-clause[] | will only be executed if search returns nothing  |

#### Put

The `put` command is used to create or update a resource.

##### Properties

| Name          | Required | Data Type     | Description                                      |
|---------------|----------|---------------|--------------------------------------------------|
| type          | yes      | string        | resource type                                    |
| id            | yes      | string        | resource id                                      |
| bodyInline    | one-of   | bytes         | inline body bytes (see Body below)               |
| bodyBlob      | one-of   | sha256 (32 B) | blob reference for spilled body (see Body below) |
| if-match      | no       | number        | the t the resource to update has to match        |
| if-none-match | no       | "*" or number | the t the resource to update must not match      |

#### Conditional Update

The `conditional-update` command is used to create or update a resource selected by search criteria. Zero matches → behaves as `create`; one match → behaves as `put` against that resource; more than one match → fails the transaction.

##### Properties

| Name          | Required | Data Type       | Description                                           |
|---------------|----------|-----------------|-------------------------------------------------------|
| type          | yes      | string          | resource type                                         |
| clauses       | yes      | search-clause[] | clauses to select the resource to update              |
| bodyInline    | one-of   | bytes           | inline body bytes (see Body below)                    |
| bodyBlob      | one-of   | sha256 (32 B)   | blob reference for spilled body (see Body below)      |
| if-match      | no       | number          | the t the resolved resource has to match (when found) |
| if-none-match | no       | "*" or number   | the t the resolved resource must not match            |

#### Delete

The `delete` command is used to delete a resource.

##### Properties

| Name       | Required | Data Type | Description                      |
|------------|----------|-----------|----------------------------------|
| type       | yes      | string    | resource type                    |
| id         | yes      | string    | resource id                      |
| check-refs | no       | boolean   | use referential integrity checks |

#### Conditional Delete

The `conditional-delete` command is used to delete possibly multiple resources by selection criteria.

##### Properties

| Name           | Required | Data Type       | Description                                      |
|----------------|----------|-----------------|--------------------------------------------------|
| type           | yes      | string          | resource type                                    |
| clauses        | no       | search-clause[] | clauses to use to search for resources to delete |
| check-refs     | no       | boolean         | use referential integrity checks                 |
| allow-multiple | no       | boolean         | allow to delete multiple resources               |

#### Delete History

The `delete-history` command is used to delete the history of a resource.

##### Properties

| Name | Required | Data Type | Description   |
|------|----------|-----------|---------------|
| type | yes      | string    | resource type |
| id   | yes      | string    | resource id   |

#### Patient Purge

The `patient-purge` command is used to remove all current and historical versions for all resources in a patient compartment.

##### Properties

| Name       | Required | Data Type | Description                      |
|------------|----------|-----------|----------------------------------|
| id         | yes      | string    | patient id                       |
| check-refs | no       | boolean   | use referential integrity checks |

**Body.** Bodies are encoded with FHIR-style polymorphic field naming: exactly one of `bodyInline` or `bodyBlob` is present on every body-bearing command. This flattens the schema (no nested map level) and keeps the variant explicit at the field name. `bodyBlob` is only produced by the distributed variant; standalone always uses `bodyInline`.

- `bodyInline` — body bytes in the transport encoding. In the distributed variant: `blaze.fhir.spec` internal CBOR. In the standalone variant: the parsed Clojure FHIR value itself (no serialization).
- `bodyBlob` — `sha256` (32 bytes) of the CBOR body bytes; the bytes themselves live in the `BlobStore` under this key.

**Outer message serialization (distributed variant).** Encoded with the same Jackson CBOR codec used by v1's Kafka tx-log (`modules/db-tx-log-kafka/src/blaze/db/tx_log/kafka/codec.clj`). `bodyInline` and `bodyBlob` ride as CBOR byte strings. Kafka record key is `null` (single partition; ordering comes from the partition, not the key).

**Standalone variant.** No message serialization: the ring buffer holds the command list by reference, with `bodyInline` carrying the parsed FHIR value directly. The same field shape is used so the rewrite and apply code is identical across variants; only the `bodyInline` reader differs (returns parsed FHIR directly instead of CBOR-decoding bytes).

**Version dedup is wire-invisible.** No-op detection happens at apply (rewrite step 4) by comparing the rewritten CBOR against the current version's `Resource` CF entry. The wire format records the intended write; whether it collapses to a no-op is a property of `db(t-1)`, not of the message.

## Apply-Loop Pipelining

Transactions still commit one at a time (one `WriteBatch` group per tx, in `t`-ascending order — see [Apply-Time Mechanics](../implementation/database.md#apply-time-mechanics)), and the rewrite stage for tx N+1 cannot start until tx N's `TxSuccess` has landed: conditional ref / conditional update resolution and version dedup all read `db(t_N)`, and that snapshot is only visible after the prior transaction's visibility-gating batch commits. So the rewrite + commit half of the apply loop stays strictly serial.

What *can* run ahead of the apply loop, for tx N+K while tx N is still committing, is the work that has no `db` dependence. The opportunity is essentially absent in the standalone variant — the in-memory tx-log already hands the apply path parsed FHIR data, with no serialization or transport on the critical path — but matters in the distributed storage variant (Kafka tx-log + optional `BlobStore`):

- **Pull commands** from the Kafka consumer.
- **`BlobStore` prefetch** for any spilled bodies (parallel I/O).
- **CBOR decode** of inline and fetched body bytes into parsed FHIR data.

By the time tx N's `TxSuccess` lands and the apply loop pulls tx N+1, its parsed bodies are already in memory and any `BlobStore` round-trips have already been paid. Pipeline depth is bounded only by consumer queue depth and a memory budget for held-but-not-yet-applied bodies.

Within a single transaction, two further pieces are worth pulling apart from the current serial flow:

- **Reference-collection traversal** in the rewrite stage (walking each body to collect `(tid, logical-id)` for the canonical sort) can fuse with the per-resource indexing traversal — both walk the same parsed body. Currently a deliberately separate pass for clarity.
- **Conditional ref and conditional update searches** for distinct commands inside one transaction are independent reads against `db(t-1)`; they should fan out across the executor pool rather than running sequentially.

These are throughput optimisations, not correctness changes. They are listed here so the apply loop's structure is explicit in the design; landing them is a follow-up to the core v2 work.

## Resource Storage

In v1 the resource store is a separate module with two backends (RocksDB for standalone, Cassandra for distributed) and is independent of the Index DB. That independence was justified by the index being reconstructible from `(resource store + tx log)`: the index DB could run with WAL sync off for write performance, and the resource store could be Cassandra in distributed mode to scale body storage horizontally. Both justifications go away in v2:

- The index is **authoritative** — WAL sync is on for the whole index DB regardless of the resource store's placement.
- Cassandra is removed.
- The stored bytes are unusable without `InternalIdToLogicalId` (for reference rendering and elided-field reconstruction) and `TxSuccess` (for `meta.lastUpdated`); the abstraction has stopped earning its boundary.

v2 collapses resource bodies into a **`Resource` column family inside the Index DB**. Standalone goes from three RocksDB instances (index, resource, tx-log) to one — tx-log was already moving in-memory in v2. Per-CF tuning is preserved (the Resource CF has its own block size, compression, and dictionary independent of the metadata CFs).

- v1 key: `sha256 content-hash (32 bytes)`, computed from the CBOR body bytes.
- v2 key: `shard-id (2), internal-id (8), num-changes (4)`, all sourced from the preceding `ResourceAsOf` lookup. In a standalone or single-shard cluster the `shard-id` is `0x0000` (or `0xFFFF` for `:global-replicated`-class resources); in a sharded cluster it places each version on the owner shard derived from that version's primary subject — see [Patient Sharding](v2-patient-sharding.md).

The value is FHIR CBOR body bytes with three storage-layer transforms applied at write time and inverted at read time. The transforms are independent of the rewrite stage (which produces the canonical post-rewrite body); they operate on the bytes that body would otherwise serialize to, and never affect command shape, transport encoding, content equality for version dedup, or any index key.

### Per-resource WriteBatch

The body write joins the existing per-resource indexing `WriteBatch` (Layer 1 of the three-layer apply, see [Apply-Time Mechanics](../implementation/database.md#apply-time-mechanics)). For each resource in a transaction, one `WriteBatch` now covers `Resource` + `SearchParamValueResource` + `ResourceSearchParamValue` + `ResourceReference` + `ReferenceResource` + `CompartmentResource` + `CompartmentSearchParamValueResource` + `PatientLastChange`. These batches commit in parallel and out of order with respect to each other, exactly as v1's per-resource search-param batches do today.

Layer 2 (tx-index batch — `ResourceAsOf`, `TypeAsOf`, `SystemAsOf`, `LogicalIdToInternalId`, `InternalIdToLogicalId`, `TypeStats`, `SystemStats`) and Layer 3 (`TxSuccess` + `TByInstant` visibility gate) are unchanged in shape; only their position relative to body writes is now formalised. Crash semantics carry over: Layer 1 commits that survive past a crash without Layer 3 sit as inert rows invisible to readers (which gate on `head(TxSuccess)`). Bodies now contribute to that inert footprint, but distributed re-apply overwrites by key (same `(internal-id, num-changes)`), and standalone's inert leak per crashed-but-uncommitted transaction is bounded by Kafka-free single-writer semantics.

Materialization within the apply of one transaction is not a constraint: the materializer reads through `TxSuccess`, which only advances after Layer 3, so no in-flight transaction's bodies are ever visible before their mappings (Layer 2) and their visibility marker (Layer 3) have landed. The layer ordering reads as deliberate, not incidental.

### Materialization API

The collapse turns the resource store from a swap-points-for-backends abstraction into a node-level service. The boundary between "stored bytes" and "canonical FHIR" stays explicit, but it moves up: the call returns canonical FHIR and takes a small **materialization context** at call time — in practice a reader for `InternalIdToLogicalId` plus a `t → instant` lookup against `TxSuccess`, both of which the caller already has on the db value.

The cache implements the same call shape and caches the **materialized FHIR**. Caching the post-materialization form is the load-bearing choice: elided-field reconstruction is cheap (`num-changes` from the key, one point read into `InternalIdToLogicalId` for `(tid, logical-id)`, one into `TxSuccess` for `lastUpdated`), but **reference rendering** is one `InternalIdToLogicalId` lookup per reference and gets expensive on resources with many references (`Bundle`, `Composition`, `Encounter`, `MedicationRequest`). A cache that stops at the intermediate form would re-pay that fan-out on every hit. Caching the materialized form lets a hot read be a single map lookup.

The cache needs no invalidation: every input to materialization (`InternalIdToLogicalId`, `TxSuccess`) is append-only, so `(internal-id, num-changes) → FHIR` entries are valid for the cache's lifetime. The `:complete` / `:summary` variant from v1 — currently used by the resource cache to hold only the FHIR summary projection of a resource — survives at the cache layer as a shape selector, not as a key component on the underlying Resource CF (only the complete form is on disk).

### Block cache strategy

The Resource CF runs with **no block cache** (`block_cache = nullptr` on its CF options). v1's reasoning carries over: ordinary FHIR reads (search hits, CQL cohort materialization, single-resource GETs) touch one resource per access, scattered across the keyspace; the temporal locality lives in the materialized-resource cache above, not below. The v2 `(internal-id, num-changes)` clustering does introduce real spatial locality for instance `_history` / `vread` (all versions of one resource contiguous in the CF) and scan-style reads (system/type `_history`, state sync, bulk export by `t`), but for the scan cases OS page cache and RocksDB sequential prefetch do most of the work, and for instance `_history` the working set is small and short-lived. A persistent block cache for the Resource CF would mostly hold blocks that won't be re-read in any time window the cache spans.

The metadata CFs that materialization repeatedly hits — `InternalIdToLogicalId` (every reference rendering, every `_history` row), `ResourceAsOf` (every read), `TxSuccess` (every `lastUpdated`) — share a healthy block cache within the one RocksDB instance. Those CFs have dense keyspaces and tight working sets where the same SST blocks serve millions of requests; their reuse is exactly what a block cache is for. Sharing one cache across these CFs is one operational win of the single-DB collapse (separate from the body storage, which doesn't benefit).

### Field elision

Four fields are derivable from the storage frame and are dropped from the stored bytes:

| Elided field         | Reconstruction source                       |
|----------------------|---------------------------------------------|
| `resourceType`       | resource type name from the `tid` table     |
| `id`                 | `InternalIdToLogicalId[internal-id]`        |
| `meta.versionId`     | `num-changes` (rendered as decimal string)  |
| `meta.lastUpdated`   | `TxSuccess[t].instant` (rendered as FHIR `instant`) |

Reconstruction runs on the materialization path, where the (`internal-id`, `num-changes`, `tid`, `t`) tuple is already in scope from the preceding `ResourceAsOf` read. `InternalIdToLogicalId` is hot for `_history` rendering anyway. For small resources (`Observation`, `Condition`, `MedicationRequest` without extensions) these four fields are 15–20% of the CBOR body.

Version dedup (rewrite step 4) byte-compares the **post-elision** stored bytes against the new write's post-elision bytes — the elided fields don't participate in equality, which is correct: a "change" that only differs in `meta.lastUpdated` is a no-op at the storage layer regardless.

### References as internal ids

Inside the stored body, every FHIR `Reference.reference` whose target was resolved to an internal id during the rewrite stage is replaced by the 8-byte internal id. Encoding uses **CBOR major-type dispatch at the `Reference.reference` slot**: the schema-aware encoder/decoder in `blaze.fhir.spec` knows that slot's value is normally a UTF-8 text string (CBOR major type 3, e.g. `"Patient/foo"`), and writes a length-8 byte string (CBOR major type 2) in its place when the reference was resolved. The decoder dispatches on the major type at that slot — text string → passthrough (unresolvable, external, or never resolved), 8-byte byte string → translate via `InternalIdToLogicalId`. No CBOR tag is involved; the marker is the major type itself, interpreted in the context of a known schema position.

Materialization translates each resolved reference back to `Type/logical-id` via a single point read into `InternalIdToLogicalId[target-internal-id]`, which carries both `tid` and `logical-id` in its value — no `ResourceAsOf` seek is needed for the rendering itself.

Per resolved reference this costs 9 bytes (1-byte CBOR prefix for the byte string + 8 id bytes) versus ≥ 12 bytes for `"Patient/x"` and ≥ 72 bytes for SHA-256-hex id deployments. Resources with many references (`Bundle`, `Composition`, `Encounter`, `MedicationRequest`, `Observation` with `component`/`hasMember`) shrink materially. Unresolved or external references (e.g. absolute URLs to other servers, references whose target is outside the database) stay as text strings; the rewrite stage flags which references it resolved.

`InternalIdToLogicalId` is already on the read path for `_history`, delete-tombstone rendering, and CQL `Resource.id` access, so the cache pressure from reference rendering shares the same SST blocks; no new CF reads are introduced per reference beyond what `_include` and reference rendering would already perform to produce a FHIR-surface response.

### Trained zstd dictionary on the Resource CF

The Resource CF is configured with zstd compression using a **pre-trained dictionary** (RocksDB `CompressionOptions.max_dict_bytes` / `zstd_max_train_bytes` or a fixed dictionary supplied at open time). The dictionary is trained once on a representative FHIR corpus (Synthea + a curated set of real-world fixtures) and shipped with the build as a binary resource loaded by the RocksDB CF descriptor at startup.

Block-level zstd already captures repetition within a block; the trained dictionary additionally captures FHIR-specific token patterns (common field name sequences, common `system` URLs, common CBOR map prologues) that don't repeat enough within a single block to be exploited by the block-local compressor. Typical wins for small-record CFs are 20–40% on top of block-only compression.

No on-the-fly retraining: the dictionary is part of the build artifact. A dictionary upgrade is a no-op for reads (zstd carries the dict id; both old and new dicts can coexist on disk during compaction) and is rolled out by shipping a new dictionary file in a release. Standalone deployments that want to retrain against their own corpus can do so via an offline tool that emits a replacement dictionary file — out of scope for the core change.

## CF Placement Classes

Every CF is one of two placement classes. The class determines where rows physically live in a sharded cluster and whether keys carry a `shard-id` prefix. In the standalone variant (and in any non-sharded interpretation of the schema) the classes degenerate but the layout is the same.

| Class               | Stored where                        | Key prefix       | Apply pattern                                                |
|---------------------|-------------------------------------|------------------|---------------------------------------------------------------|
| **Global**          | Every node, full content            | No `shard-id`    | Layer 2 (tx-index) on every node; identical content cluster-wide |
| **Sharded**         | Owners of the relevant shard only   | 2-byte `shard-id` prefix on every key | Layer 1 (per-resource) only on the owning node(s) |

- **Global** CFs: `LogicalIdToInternalId`, `InternalIdToLogicalId`, `ResourceAsOf`, `TypeAsOf`, `SystemAsOf`, `TxSuccess`, `TxError`, `TByInstant`, `TypeStats`, `SystemStats`, `ClusterConfig`.
- **Sharded** CFs: `Resource`, `SearchParamValueResource`, `ResourceSearchParamValue`, `CompartmentResource`, `CompartmentSearchParamValueResource`, `PatientLastChange`, `ResourceReference`, `ReferenceResource`.

Constants live outside the CF system: the `tid` table at `modules/db/src/blaze/db/impl/codec/tid.clj` and the placement table at `modules/db/src/blaze/db/impl/codec/placement.clj` are Clojure namespaces compiled into the binary. The trained zstd dictionary is a binary build artifact loaded into the `Resource` CF's compression options at startup — it's a configuration parameter, not a CF.

For sharded CFs, the `shard-id` is the row's **anchor shard** — the owner of the version (for `Resource`, the per-version sharded indices, and forward references), the compartment patient's shard (for compartment indices), the patient's own shard (for `PatientLastChange`), or the reference target's owner shard (for reverse references). A `:global-replicated`-class resource (e.g., a `Practitioner`, or a `:patient-scoped` version whose subject doesn't resolve) carries `shard-id = 0xFFFF` and is written on every node.

`shard-id` placement is **per-version, not per-resource**. Subsequent versions of the same resource may carry different `shard-id` values — a subject change or a `Patient/$merge` re-subjects later versions to a different patient and lands those versions on the new shard. The `shard-id` for the version-at-`t` is read from `ResourceAsOf.value`. See [Patient Sharding](v2-patient-sharding.md) for the full placement rule, the `:patient-root` / `:patient-scoped` / `:global-replicated` classification, and the per-version locality invariant.

## Index Layout Changes (v2)

| Index                               | v1 Key                                                                               | v2 Key                                                                                             | v1 Value                                                     | v2 Value                                           | Notes                                                                                                                                        |
|-------------------------------------|--------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| ResourceAsOf                        | `tid (4), id (≤64), t (8)`                                                           | `internal-id (8), t-desc (6)`                                                                                  | `content-hash (32), num-changes (7), op (1), purged-at? (8)` | `tid (2), num-changes (4), op (1), shard-id (2), purged-at? (6)` | `tid` moves from key to value; `content-hash` dropped; `shard-id` records this version's owner shard                          |
| TypeAsOf                            | `tid (4), t (8), id (≤64)`                                                           | `tid (2), t-desc (6), internal-id (8)`                                                                         | `content-hash (32), num-changes (7), op (1), purged-at? (8)` | `num-changes (4), op (1), shard-id (2), purged-at? (6)`          | `tid` already in key, dropped from value; type history in t order; `shard-id` records this version's owner shard              |
| SystemAsOf                          | `t (8), tid (4), id (≤64)`                                                           | `t-desc (6), tid (2), internal-id (8)`                                                                         | `content-hash (32), num-changes (7), op (1), purged-at? (8)` | `num-changes (4), op (1), shard-id (2), purged-at? (6)`          | `tid` already in key, dropped from value; system-wide history in t order; `shard-id` records this version's owner shard       |
| PatientLastChange                   | `patient-id (≤64), t-desc (8)`                                                       | `shard-id (2), patient-internal-id (8), t-desc (6)`                                                            | empty                                                        | empty                                              | Fixed-width key prefix; sharded by patient                                                                                                   |
| SearchParamValueResource            | `c-hash (4), tid (4), value (var), id (≤64), hash-prefix (4)`                        | `shard-id (2), c-hash (4), tid (2), value (var), internal-id (8), num-changes (4)`                             | empty                                                        | empty                                              | **Non-reference params only.** Random `hash-prefix` replaced by dense, ordered `num-changes`; sharded by version owner                       |
| ResourceSearchParamValue            | `tid (4), id (≤64), hash-prefix (4), c-hash (4), value (var)`                        | `shard-id (2), tid (2), internal-id (8), num-changes (4), c-hash (4), value (var)`                             | empty                                                        | empty                                              | **Non-reference params only.** Same substitution; sharded by version owner                                                                   |
| ResourceReference (new)             | —                                                                                    | `shard-id (2), internal-id (8), num-changes (4), c-hash (4), target-tid (2), target-internal-id (8)`           | —                                                            | empty                                              | Forward: this version references that target via search param `c-hash`. `target-tid` retained for typed-modifier filtering; sharded by source's owner |
| ReferenceResource (new)             | —                                                                                    | `shard-id (2), target-internal-id (8), c-hash (4), tid (2), internal-id (8), num-changes (4)`                  | —                                                            | empty                                              | Reverse: this target is referenced by that source version via search param `c-hash`. Source `tid` retained for typed `_revinclude` filtering; sharded by target's owner |
| CompartmentResource                 | `c-hash (4), comp-id (≤64), tid (4), id (≤64)`                                       | `shard-id (2), c-hash (4), comp-internal-id (8), tid (2), internal-id (8)`                                     | empty                                                        | empty                                              | Both ids become internal; sharded by compartment patient                                                                                     |
| CompartmentSearchParamValueResource | `c-hash (4), comp-id (≤64), sp (4), tid (4), value (var), id (≤64), hash-prefix (4)` | `shard-id (2), c-hash (4), comp-internal-id (8), sp (4), tid (2), value (var), internal-id (8), num-changes (4)` | empty                                                        | empty                                              | Same; sharded by compartment patient                                                                                                         |
| TxSuccess                           | `t (8)`                                                                              | `t (6)`                                                                                            | tx metadata (instant, …)                                     | tx metadata (instant, …)                           | Key narrowed; value unchanged                                                                                                                |
| TxError                             | `t (8)`                                                                              | `t (6)`                                                                                            | error details                                                | error details                                      | Key narrowed; value unchanged                                                                                                                |
| TByInstant                          | `instant`                                                                            | `instant`                                                                                          | `t (8)`                                                      | `t (6)`                                            | Value narrowed                                                                                                                               |
| TypeStats, SystemStats              | unchanged                                                                            | unchanged                                                                                          | counters                                                     | counters                                           | Unchanged                                                                                                                                    |
| LogicalIdToInternalId (new)         | —                                                                                    | `tid (2), logical-id (≤64)`                                                                        | —                                                            | `internal-id (8)`                                  | Allocate-on-first-sight mapping                                                                                                              |
| InternalIdToLogicalId (new)         | —                                                                                    | `internal-id (8)`                                                                                  | —                                                            | `tid (2), logical-id (≤64)`                        | Reverse of the above; one entry per resource. Source of truth for `(tid, logical-id)` when body is unavailable (delete tombstone, all-purged history) and for single-point-read reference rendering during body materialization |
| Resource (new CF in Index DB)       | `sha256 (32)` *(separate DB in v1)*                                                  | `shard-id (2), internal-id (8), num-changes (4)`                                                   | CBOR body                                                    | CBOR body with field elision + internal-id refs    | Body storage collapsed into the Index DB as the `Resource` CF; zstd + trained dictionary; no block cache; sharded by version owner. See *Resource Storage* |

Summed sizes per entry, in bytes. `id` / `patient-id` / `comp-id` are FHIR logical ids (≤ 64 bytes); `value` is the search-parameter value (variable). v2 keys are fixed-width wherever ids appear.

| Index                               | v1 Key                                    | v2 Key                | v1 Value          | v2 Value          |
|-------------------------------------|-------------------------------------------|-----------------------|-------------------|-------------------|
| ResourceAsOf                        | 12 + id (≤ 76)                            | 14                    | 40 (+8 if purged) | 9 (+6 if purged)  |
| TypeAsOf                            | 12 + id (≤ 76)                            | 16                    | 40 (+8 if purged) | 7 (+6 if purged)  |
| SystemAsOf                          | 12 + id (≤ 76)                            | 16                    | 40 (+8 if purged) | 7 (+6 if purged)  |
| PatientLastChange                   | 8 + patient-id (≤ 72)                     | 16                    | 0                 | 0                 |
| SearchParamValueResource            | 16 + value + id (≤ 80 + value)            | 20 + value            | 0                 | 0                 |
| ResourceSearchParamValue            | 12 + id + value (≤ 76 + value)            | 20 + value            | 0                 | 0                 |
| ResourceReference (new)             | —                                         | 28                    | —                 | 0                 |
| ReferenceResource (new)             | —                                         | 28                    | —                 | 0                 |
| CompartmentResource                 | 8 + comp-id + id (≤ 136)                  | 24                    | 0                 | 0                 |
| CompartmentSearchParamValueResource | 16 + comp-id + value + id (≤ 144 + value) | 32 + value            | 0                 | 0                 |
| TxSuccess                           | 8                                         | 6                     | tx metadata       | tx metadata       |
| TxError                             | 8                                         | 6                     | error details     | error details     |
| TByInstant                          | instant                                   | instant               | 8                 | 6                 |
| TypeStats, SystemStats              | unchanged                                 | unchanged             | counters          | counters          |
| LogicalIdToInternalId (new)         | —                                         | 2 + logical-id (≤ 66) | —                 | 8                 |
| InternalIdToLogicalId (new)         | —                                         | 8                     | —                 | 2 + logical-id (≤ 66) |
| Resource (CF in Index DB)           | 32                                        | 14                    | CBOR body         | reduced CBOR body |

Sharded CFs each gain 2 bytes per key for `shard-id`; `*AsOf` global CFs each gain 2 bytes per value. RocksDB's prefix-shared block encoding absorbs most of the per-row addition — realistic SST inflation is ≤ 1%.

`num-changes` is encoded as a 4-byte big-endian unsigned int (2^32 ≈ 4.3 B versions per resource), well beyond any realistic update frequency including IoT/ETL workloads that PUT the same resource in a tight loop. Overflow returns an `:cognitect.anomalies/conflict` anomaly recorded in `TxError`.

The single-version-id concept in `modules/db/src/blaze/db/impl/index/single_version_id.clj` collapses: it becomes just `num-changes`, no hash component.

## Query / Search Path

The query planner (`modules/db/src/blaze/db/impl/index/plan.clj`) is structurally unchanged: SCANS/SEEKS split, selectivity estimation, scan-size grouping. What changes:

- Index handles carry `internal-id` (8 bytes) and `num-changes` (4 bytes) instead of `logical-id` (variable) and `hash-prefix` (4 bytes).
- The final conversion to a resource handle is the same seek into `ResourceAsOf` as today, but with the v2 key shape (`internal-id (8), t-desc (6)`), returning `tid`, `num-changes`, `op`, `purged-at?` from the value.
- Reference search params no longer live in `SearchParamValueResource`/`ResourceSearchParamValue`. Reference-valued lookups go through the new `ResourceReference` / `ReferenceResource` CFs, both keyed entirely in internal ids.
- Chained search (`modules/db/src/blaze/db/impl/search_param/chained.clj`) walks `ResourceReference` (forward) hop-by-hop. The translation from a parsed reference value (`Patient/foo`) to the target internal id goes through `LogicalIdToInternalId` once at query-plan time.
- `_include` uses `ResourceReference`; `_revinclude` uses `ReferenceResource`. Both traverse internal ids; the logical ids needed for the final serialized response are read from the resource body when materializing.
- **Pagination cursor (`__page-id`)** switches from logical-id to internal-id (rendered as a fixed-width opaque token). Continuation tokens are already documented as opaque to clients; the wire shape changes but the contract does not. Tokens minted by a v1 server are not honored by v2 — acceptable given v2 is a clean break with no on-disk compatibility and tokens are short-lived.

### Sorting

v1 sorts only a small whitelist (`_id`, `_lastUpdated`, `birthdate`); v2 defines sorting directly on the index structures so it has one principled story rather than a set of special cases.

**Default order (no `_sort`): internal-id ascending.** The internal id is fixed-width, globally unique, and carries `t` in its high bits, so internal-id order coincides with `(t, counter)` order — a stable, total order that doubles as the implicit page order. This replaces v1's logical-id ordering; the pagination cursor keys on internal-id.

**Sort semantics.** A `_sort` parameter orders results by the resource's *minimum* value for the parameter when ascending and its *maximum* when descending — the FHIR convention for multi-valued elements; for single-valued elements the two coincide. The emitted order key is `(sort-value, internal-id)`, with internal-id breaking ties, so the order is total and every cursor position is unambiguous.

**Two execution modes.** A sorted query can be driven from either side — walk the sort parameter's value-ordered index and filter, or walk the filter's matches and order them. Their costs are mirror images. With page size `P`, type cardinality `N` (from `TypeStats`), and filter result size `R`:

| Mode               | Driving stream                   | Per-page cost                                 | Memory |
|--------------------|----------------------------------|-----------------------------------------------|--------|
| **Scan-sorted**    | sort param's value-ordered index | `~P·N/R` entries scanned (stops at page fill) | O(1)   |
| **Buffered top-K** | most selective search clause     | `~R` (full filter enumeration)                | O(P)   |

- **Scan-sorted** iterates `SearchParamValueResource` — keyed `(c-hash, tid, value, internal-id, num-changes)`, `value` ahead of the ids — in key order (reverse for descending); the scan *is* the sort. All search clauses are demoted to **matchers** that post-filter each scanned candidate, so the disjunction-union machinery never runs: a single driving stream has no cross-clause duplicates to merge away. A resource with several values for the sort parameter appears once per value; the **min/max emission rule** deduplicates statelessly — emit a hit only when the scanned value equals the resource's minimal (ascending) / maximal (descending) value for the parameter, established by one point read into `ResourceSearchParamValue`. Early termination at page fill makes this mode unbeatable for broad filters.
- **Buffered top-K** runs the query exactly as the unsorted planner would — most selective clause drives, `ordered-index-handles` (distinct, id-ordered) unions and deduplicates disjunctions — and selects the page with a bounded heap: keep the `P` smallest `(sort-value, internal-id)` pairs greater than the resume bound, emit the heap in order. Memory is O(P), not O(R) — the per-page bound turns "sort the result set" into per-page top-K selection; each page re-enumerates the filter, which is cheap in exactly the regime where this mode wins. The sort value per handle is one point read into `ResourceSearchParamValue` (taking the min/max for multi-valued parameters — duplicate-free by construction, since handles are enumerated rather than values); for `_lastUpdated` it is free, because the handle already carries its version's `t`.

**Mode choice is a planner cost decision.** Scanning is cheaper iff `P·N/R < R`, i.e. `R > √(P·N)` — for `N = 100 M`, `P = 50` the crossover sits near `R ≈ 70 k` matches. Both inputs are already in the planner's vocabulary (`estimated-scan-size` for the filter side, `TypeStats` for `N`), so the choice is one more cost comparison in `plan.clj`, not new machinery.

**The continuation cursor is a bare start-id, as in v1.** The persisted cursor is the last emitted internal-id. Under `_sort` the executor re-derives the full resume bound `(sort-value, internal-id)` from it with one point read into `ResourceSearchParamValue` at the pinned `t` (or `ResourceAsOf` / `LogicalIdToInternalId` for the special parameters) — deterministic, because the snapshot is immutable across pages. The sort value never needs to round-trip through the token.

**Misestimation is corrected at runtime, not suffered.** Both modes emit the same total order and resume from the same cursor — the mode is invisible in the continuation token and may differ from page to page. A query that starts scan-sorted tracks its scanned-to-emitted ratio; when it exceeds budget, the executor aborts the scan and restarts in buffered mode from the last emitted cursor position. Nothing already emitted is wasted; the bail-out is pure cost control.

**Special parameters.** `_lastUpdated` needs no dedicated index rows: `TypeAsOf` (`tid, t-desc, internal-id`) is its value-ordered index, since `lastUpdated` is a function of `t` — iterated natively for descending, in reverse for ascending. `_id` (the logical id) sorts over `LogicalIdToInternalId` (`tid, logical-id`), which is logical-id-ordered per type; entries for phantoms and deleted resources fall to the same current-version validity check every read path applies.

**Sortable ⇔ value-ordered index.** Buffered mode alone would need only a per-handle value lookup, but scan-sorted must exist as the fallback for broad filters — where buffering is exactly what's unaffordable — so a parameter is sortable iff it has a value-ordered index, and v2 rejects `_sort` on parameters without one. The same property lets a sharded cluster merge per-shard sorted streams without buffering anywhere — see [Patient Sharding › Merge, ordering, and dedup](v2-patient-sharding.md#merge-ordering-and-dedup).

## Critical Files

**New code:**

- `modules/db/src/blaze/db/impl/codec/tid.clj` — static R4 type-name ↔ 2-byte tid table; append-only.
- `modules/db/src/blaze/db/impl/codec/internal_id.clj` — encode/decode the 8-byte internal id; counter state for current tx.
- `modules/db/src/blaze/db/impl/index/logical_to_internal.clj` — `LogicalIdToInternalId` CF reader/writer + the per-transaction allocator. Writes both forward and reverse CFs atomically at allocation. Fronted by a Caffeine cache (load on miss, no invalidation — entries are append-only); used by the rewrite stage for bundle placeholder resolution, conditional reference resolution, and per-body reference translation.
- `modules/db/src/blaze/db/impl/index/internal_to_logical.clj` — `InternalIdToLogicalId` CF reader/writer. Read-side helper used by `_history` rendering, CQL `Resource.id` access, and state-sync source on v2. Fronted by a Caffeine cache (load on miss, no invalidation); on the materialization hot path for reference rendering — see *In-memory caches*.
- `modules/db/src/blaze/db/impl/index/resource_reference.clj` — `ResourceReference` CF (forward, source-keyed) reader/writer.
- `modules/db/src/blaze/db/impl/index/reference_resource.clj` — `ReferenceResource` CF (reverse, target-keyed) reader/writer.
- `modules/db/src/blaze/db/node/tx_indexer/rewrite.clj` — the rewrite stage (placeholders, conditional refs, conditional updates, version dedup, internal-id resolution).
- `modules/db-blob-store/src/blaze/db/blob_store.clj` — `BlobStore` protocol.
- `modules/db-blob-store-s3/src/blaze/db/blob_store/s3.clj` — S3-compatible reference impl. Sha256-hex keys, time-based retention configured on the bucket side.

**Heavily modified:**

- `modules/db/src/blaze/db/impl/index/resource_as_of.clj` — new key shape `internal-id (8), t-desc (6)` (tid dropped from key); value drops `content-hash`, gains `tid (2)`, retains `num-changes`, `op`, `purged-at?`. Same callers, same access pattern.
- `modules/db/src/blaze/db/impl/index/resource.clj` (new) — `Resource` CF inside the Index DB (replaces the independent `db-resource-store` module). Key `internal-id (8), num-changes (4)`. Encodes/decodes the elision + internal-id-ref transforms; configured with zstd + trained dictionary; CF block cache disabled. Materialization API takes a context carrying `InternalIdToLogicalId` and `TxSuccess` readers and returns canonical FHIR.
- `modules/db/src/blaze/db/cache/resource_cache.clj` — caches the **materialized** FHIR form keyed by `(internal-id, num-changes)`. No invalidation (all materialization inputs are append-only). `:complete` / `:summary` variant survives at this layer as a cache-internal shape selector.
- `modules/db/src/blaze/db/impl/codec.clj` — `tid` rewritten as a table lookup (2 bytes); `id-byte-string` callsites in indices replaced by `internal-id` encoding; `id-byte-string` itself kept for the `LogicalIdToInternalId` key path only.
- `modules/db/src/blaze/db/node/transaction.clj` — `prepare-op`: stop computing the content hash; drop the `:keep` multimethod branch. In the standalone variant, pass parsed FHIR data through to the in-memory tx-log without serializing. In the distributed storage variant, implement the submitter-side inline/spill algorithm with the threshold sourced from the Kafka tx-log adapter. Version dedup is decided at apply time, not at submission.
- `modules/db/src/blaze/db/node/tx_indexer.clj` — wire the new rewrite stage before `expand`/`verify`. Apply path: parallel blob fetch + sha256 verify before rewrite stage; otherwise unchanged.
- `modules/db/src/blaze/db/node/tx_indexer/expand.clj` — adapt to consume rewritten commands; conditional delete / conditional update converge through the same rewrite mechanism.
- `modules/db/src/blaze/db/node/tx_indexer/verify.clj` — references checked via internal ids; phantom allocation path replaces the "doesn't exist" branch under ref-integrity off.
- All `modules/db/src/blaze/db/impl/index/*.clj` listed in the layout table.
- `modules/db/src/blaze/db/impl/index/single_version_id.clj` — collapses to `num-changes` only.
- `modules/db/src/blaze/db/impl/index/resource_handle.clj` — `resource-handle!` carries internal id only; the handle no longer has an `:id` field. Logical id is resolved on demand via `InternalIdToLogicalId` (always available, including for delete tombstones and all-purged histories) or read from the body when the body is being materialized anyway. The Java `ResourceHandle` class drops its `id` field; `ID_CMP`, `equals`, and `hashCode` switch to internal-id. The `tid-id` helper is removed (callers switch to internal-id directly).
- `modules/cql/src/blaze/elm/resource.clj` — CQL `Resource` wrapper holds the internal id and resolves `:id` lazily via `InternalIdToLogicalId` (preserving the existing optimization that allows `Patient.id` access without body materialization).
- `modules/db/src/blaze/db/impl/search_param/*.clj` — every site that builds index keys from `id`. Reference-typed search params (`reference.clj` and callers in `chained.clj`) no longer write to `SearchParamValueResource`/`ResourceSearchParamValue`; they write to `ResourceReference` + `ReferenceResource` instead and read from the same.
- `modules/db-tx-log-kafka/src/blaze/db/tx_log/kafka.clj` — single-topic `blaze-tx` only. Producer reads `max.message.bytes` via `AdminClient` at startup with periodic refresh / refresh-on-error.
- `modules/db-tx-log-local/` — standalone tx-log impl becomes in-memory ring buffer for subscriptions; no RocksDB CF for the log itself. Apply path keeps the v1 three-layer `WriteBatch` structure.
- `modules/interaction/src/blaze/interaction/transaction/bundle.clj` and `bundle/links.clj` — bundle-internal placeholder resolution moves to apply time; the interaction layer stops doing it (or does it only for client-facing response construction).
- `modules/interaction/src/blaze/interaction/search/include.clj` — operates on internal ids.

**Deleted:**

- `modules/db-resource-store/` — entire module. Body storage moves into the Index DB as a CF; the `ResourceStore` swap-for-backends protocol is replaced by the in-tree materialization API described above.
- `modules/db-resource-store-cassandra/` — entire module.
- `modules/db-tx-log-kafka/src/blaze/db/tx_log/kafka/body_buffer.clj` — was in the prior revision; never lands. The in-memory submission-uuid → body map, applied-submissions ring, and bodies-rewind logic that justified it are removed from scope.

**Build matrix:**

- `.github/workflows/build.yml` — drop `db-resource-store` and `db-resource-store-cassandra` from the module list; remove Cassandra-related CI steps; add `db-blob-store` and `db-blob-store-s3` (the latter with MinIO docker service for integration tests).

**Docs:**

- `docs/implementation/database.md` — rewrite index tables, add Internal ID section, add Rewrite Stage section, remove content-hash-as-key references, document the WAL-style log retention model.
- `docs/deployment/environment-variables.md` — remove Cassandra env vars; add `BlobStore` configuration (S3 endpoint, bucket, credentials); document that Kafka `max.message.bytes` drives the inline threshold.

## Node Bootstrap (State Sync)

A single network protocol — **state sync** — handles two cases that are structurally the same:

1. **v1 → v2 migration.** A fresh v2 node pulls the authoritative state from a v1 node.
2. **Late-joining v2 node.** A v2 node that joins (or rejoins) a cluster after the tx-log retention window has discarded the transactions it needs cannot catch up via the log alone. It pulls state from any healthy v2 peer, then tails the log from there.

Because both flows transport authoritative state over the network, there is no file-based migration path, no mounted v1 volume, no RocksDB snapshot copy.

**The stream carries full version history**, not just the live tip. FHIR `_history` (instance/type/system) and `vread` require every version to remain queryable on the bootstrapped node; transferring only the live state would silently degrade conformance. Transfer volume scales with total version count rather than live-resource count; operators with heavy update churn should size the bootstrap window accordingly.

### Protocol

A new admin endpoint under `/__admin/state-sync` (new module `modules/db-state-sync/`) exposes every version of every resource. Implemented by **both v1 and v2** servers (v1 implementation back-ported alongside the v2 work). Consumed only by v2 nodes during bootstrap. Stateless on the server.

**`GET /__admin/state-sync?start-t=<int>`:**

- Server opens a `SystemAsOf` iterator. The iterator's implicit snapshot fixes the view for the duration of the call.
- Iterates rows with `t ≥ start-t` in `(t, tid, id)` order. Emits one frame per version. The iterator terminates at the end of the snapshot's content; the highest `t` actually emitted is `t_end`.
- Response: `200 OK`, `Transfer-Encoding: chunked`, `Content-Type: application/cbor-seq` (RFC 8742). The body is a sequence of self-delimiting CBOR items, one per frame.
- Final frame is `{ end: t_end }`. The client persists `t_end` as its new `start-t` for the next call (or as the tx-log tail-start once it transitions to live mode).
- A connection close without an `end` frame is treated by the client as truncation; the client retries with `start-t` set to the highest `t` it has fully imported (i.e. the highest `t` whose entire transaction was received). Earlier transactions are already on disk and need not re-stream.
- Backpressure is TCP flow control. The server's iterator advances only when the socket is writable; no application-layer credits.

**`GET /__admin/state-sync/status`:** returns a small JSON body with the source's current `t` and protocol version.

```
GET /__admin/state-sync/status
  → 200 OK
    Content-Type: application/json
    Body: { "current_t": <int>, "source_version": 1 | 2 }
```

Cheap — `current_t` is the head of `TxSuccess`. JSON for operator inspectability. Implemented by both v1 (back-ported) and v2.

**Authentication** uses the same mechanism as other Blaze admin endpoints (bearer token or mTLS, deployment-dependent). State-sync exposes the full database; no anonymous path.

**Resumability and idempotency.** The client persists `start-t` and updates it on each successful `end` frame. On retry, the client re-sends its last durably-persisted `start-t`. Some frames at the boundary `t` may be re-received; client writes are idempotent — same `(internal-id, t-desc)` keys, same values, same bodies — so applying them twice is a no-op. Frames are always emitted at transaction boundaries (all rows for one `t` are contiguous), so the boundary case is "the entire transaction at `start-t` re-streams," never a half-applied transaction.

**Convergence.**

The iterator's implicit snapshot bounds the stream at `t_end`. Mutations to rows with `t ≤ t_end` that occur after the iterator opens are caused by transactions at `t > t_end` and are not visible to this call. The client picks them up either on a subsequent `GET` (with `start-t = previous t_end + 1`) or by transitioning to tx-log tailing from `t_end + 1`.

In-place value mutations (`purged-at?` flipping on a row whose create/put has been emitted) and body deletes in the `Resource` CF ride the same path: tx-log replay on the client applies the purge transaction and brings local state in line with the source. The client tolerates `body` absent in any frame — a version purged before this call's iterator opened would have no body either.

**Frame schema:**

The stream begins with a single header frame carrying a protocol version and is followed by data frames whose shape is determined by that version.

```
header  := { version: <int>, current_t: <int> }
tx-meta := { t: <int>, instant: <int> }                          // ms since epoch
end     := { end: t_end }

// version = 1 (v1 Blaze sources)
data    := { t, tid, logical-id, num-changes, op, purged-at?, body? }

// version = 2 (v2 Blaze sources)
data    := { t, tid, internal-id, num-changes, op, purged-at?, body?, logical-id? }   // logical-id present iff num-changes = 1
```

The stream order is: one `header`, then for each transaction in ascending `t` order one `tx-meta` followed by its `data` frames, then `end`. The `tx-meta` frame carries the transaction's commit instant, which the importer writes to `TxSuccess` (and `TByInstant`) as the final per-transaction visibility-gating `WriteBatch` — see *Common to both paths*.

- **Version 1** (v1 sources): `logical-id` read straight from v1's `SystemAsOf` key, present on every frame — the client-side mint needs `(tid, logical-id)` on every frame to drive the per-transaction canonical sort.
- **Version 2** (v2 sources): `internal-id` is the iterator's key; `logical-id` is included only on the first version of each resource (`num-changes = 1`), obtained via a point lookup in `InternalIdToLogicalId`.

The importer reads the header, dispatches once on `version`, and runs the corresponding import path for the rest of the stream. Future server releases can introduce new versions by bumping the header value.

There is no reserved `t=0` block. For v2-source frames, the imported internal ids retain whatever `t` bits the source assigned, preserving intra-transaction locality. For v1-source imports the importer assigns fresh ids using the same canonical sort the live v2 apply uses, so the resulting cluster is bit-identical to a v2-from-day-1 cluster that processed the same tx history live.

**Body availability invariant.** The client-side mint for v1 sources walks every create/put body to collect references, and the mint requires every such body to be present (a missing body would let a hidden reference escape the canonical sort, breaking determinism). The invariant is satisfied by v1's storage semantics: **v1 never deletes bodies.** Body deletion is a v2-only feature (`delete-history` / `patient-purge` removing bodies from the resource store). Since the only minting consumer is the v1→v2 importer, every body the mint needs is on the source. A frame with `op = delete` has no body, but contributes only `(tid, logical-id)` to the union — no references to walk.

**Server side (common):**

For `GET /__admin/state-sync?start-t=N`:

1. Emit the header frame `{ version: <1 | 2>, current_t: <int> }`. `current_t` is the source's current `t` (head of `TxSuccess`) at the moment the iterator opens. v1 servers emit `version: 1`; v2 servers emit `version: 2`.
2. Open a `SystemAsOf` iterator on the live DB and iterate it in reverse — `SystemAsOf` is keyed by `t-desc`, so reverse iteration yields rows in ascending `t` order, which is the order frames are emitted. Seek to `start-t`.
3. For each `t` encountered, emit one `tx-meta` frame (looked up from `TxSuccess`) immediately before the `data` frames belonging to that `t`. The transition is detected by the iterator's `t` changing relative to the previous row.
4. Emit one data frame per version reached by the iterator. Body is included for `op ∈ {create, put}` when present in the `Resource` CF. The `purged-at?` value is taken from the row's value as seen under the snapshot. Track the highest emitted `t` as `t_end`.
5. On end-of-iteration, emit `{ end: t_end }`.

**v2 source side:** the live DB has `InternalIdToLogicalId`. The handler sets `internal-id` on every frame from the iterator's key. On frames with `num-changes = 1` it also sets `logical-id` via a point lookup in `InternalIdToLogicalId`. The lookup is cheap: a `num-changes = 1` row's internal-id was allocated at the row's own `t`, so the internal-id's high-44-bit `t` prefix tracks the scan's `t`. Lookups across the scan therefore progress through `InternalIdToLogicalId` in broadly ascending key order — each SST block is loaded once and serves the cluster of lookups whose internal-ids fall into its range.

**v1 source side:** the handler reads `(t, tid, logical-id)` straight out of v1's `SystemAsOf` key, plus `(num-changes, op, purged-at?)` from the value, and emits one frame per row. Every frame carries `logical-id`; `internal-id` is never set. Minting happens on the client side.

**Client (v2 importer) side:**

The importer reads the header frame, dispatches on its `version`, and runs one of two import paths for the rest of the stream.

1. `GET /__admin/state-sync?start-t=N` against the source, where `N` is the client's durably-persisted resume point (initially `0`).
2. Read the header frame and dispatch on `version`:
   - `2` → **v2-source path** (pipelined, no minting).
   - `1` → **v1-source path** (per-transaction buffered, with client-side minting).

**v2-source path** (pipelined within a transaction). A bounded queue separates the I/O reader from a thread pool of indexer workers. Workers do not need to be sharded: every CF the importer touches is keyed by `(..., internal-id, num-changes)` or `(internal-id, t-desc)`, so writes for distinct versions never collide; mapping-CF upserts are idempotent. Apply mirrors the v1 three-layer pattern (see [Apply-Time Mechanics](../implementation/database.md#apply-time-mechanics)): per-resource indexing `WriteBatch`es first, then a tx-index `WriteBatch`, then a `TxSuccess`/`TByInstant` `WriteBatch` as the visibility gate.

- **Reader thread** — for each frame in stream order:
  - If `logical-id` is present (`num-changes = 1`): record `(tid, logical-id) ↔ internal-id` in the in-memory mapping for the current transaction; mappings are flushed to `LogicalIdToInternalId` / `InternalIdToLogicalId` as part of the tx-index batch below. Idempotent — re-writes for the same resource (re-stream after retry) are no-ops.
  - Accumulate the v2-key-shape `ResourceAsOf` / `TypeAsOf` / `SystemAsOf` rows for this version into the in-progress tx-index batch.
  - If body present: hand the parsed body (with its internal id and `num-changes`) to the indexer pool, which writes the `Resource` CF entry together with the per-resource indexing batch in parallel with other resources in the same transaction.
- **Indexer workers** — for each handed-off `(version, body)`: write the `Resource` CF entry under `(internal-id, num-changes)` together with the per-resource `WriteBatch` populating `SearchParamValueResource`, `ResourceSearchParamValue`, `ResourceReference`, `ReferenceResource`, `Compartment*`, `PatientLastChange`. All these CFs are keyed with `num-changes` (or `t-desc`) as a version discriminator, so entries from distinct versions of the same resource coexist as distinct rows. Writes are independent across workers.
- **Transaction commit** — on encountering the next `tx-meta` (or `end`): wait for the in-flight per-resource batches of the just-finished transaction, commit the accumulated tx-index `WriteBatch` (mappings + `*AsOf` family + `*Stats` deltas), then commit a final `WriteBatch` with `TxSuccess` and `TByInstant`. Commits proceed in `t`-ascending order so `head(TxSuccess)` is always the durable resume point.

**v1-source path** (buffered, with client-side minting). Frames are accumulated by transaction; minting and indexing happen at each transaction boundary.

- **Per-transaction buffer.** While the reader sees frames with the same `t`, it accumulates them — each as `(t, tid, logical-id, num-changes, op, purged-at?, body?)`. When the next frame's `t` differs, the buffered transaction is complete; flush it before processing the new frame.
- **Flush sequence for a complete transaction:**
  1. **Reference collection pass.** Walk each buffered body once and extract every `Reference` element's `(tid, logical-id)`. This is a separate traversal from the indexing pass — kept deliberately distinct to leave search-param indexing untouched; the two passes can be fused as a later optimisation.
  2. **Build the union set.** Combine each frame's `(tid, logical-id)` and every reference's `(tid, logical-id)`. Filter to those not already in the client's `LogicalIdToInternalId`.
  3. **Canonical sort.** Sort the filtered new pairs by raw `(tid-bytes, logical-id-bytes)` ascending. This is the same sort v2 uses at apply time.
  4. **Allocate.** Assign internal ids in sorted order, advancing the per-transaction counter from 0. The id's `t` bits are the transaction's `t`. Mappings for `LogicalIdToInternalId` / `InternalIdToLogicalId` are accumulated in memory; they are persisted as part of the tx-index batch in step 5 so that the entire transaction's visibility is gated by `TxSuccess`.
  5. **Process frames — three-layer commit, same as live apply.**
     1. **Per-resource indexing batches (parallel).** For each buffered frame with a body, dispatch to the indexer pool: one per-resource `WriteBatch` covering the `Resource` CF entry under `(internal-id, num-changes)` together with the search-param / reference / compartment / patient-last-change CFs. References inside the body resolve via the now-accumulated mappings.
     2. **Tx-index batch.** Wait for the per-resource batches, then commit one `WriteBatch` with the mappings (`LogicalIdToInternalId` / `InternalIdToLogicalId`), the v2-key-shape `ResourceAsOf` / `TypeAsOf` / `SystemAsOf` rows for every frame in the transaction, and `TypeStats` / `SystemStats` deltas.
     3. **`TxSuccess` batch (visibility gate).** Commit `TxSuccess` and `TByInstant`. Importers commit this in `t`-ascending order.
- **Determinism.** Multiple v2 nodes importing from the same v1 source independently arrive at byte-identical internal ids: each transaction's union depends only on the source's frame bytes and the bodies referenced therein; the canonical sort is purely lexicographic; the counter resets per transaction. The body-availability invariant (v1 never deletes bodies) ensures the union is complete on every importer.

**Common to both paths:**

1. Each transaction's `tx-meta` frame (received before its `data` frames) carries `(t, instant)`. After the per-resource and tx-index batches for that transaction have committed, the importer writes `TxSuccess` and `TByInstant` in a final per-transaction `WriteBatch` — the visibility gate, mirroring v1 / v2 live apply. `TxSuccess` commits are issued in `t`-ascending order so `head(TxSuccess)` is always the durable resume point: rows in `ResourceAsOf` and the per-resource CFs may exist for `t > head(TxSuccess)` after a crash, but they're inert (readers gate on `head(TxSuccess)`) and the re-streamed frames overwrite them idempotently.
2. On `end` frame: drain the indexer pool, ensure any trailing transaction's three-layer commit has completed.

The transition to `:live` is operator-driven from `:migration` and automatic from `:joining` — see *Node mode*.

### Node mode

A v2 node has three modes, persisted under a single `"mode"` key in the index DB's default CF (alongside the existing `"version"` key handled by `blaze.db.node.version`), loaded at startup, never controlled by environment variables. The value is a small map:

```
{ :mode   :live | :migration | :joining
  :source <url-string>   ; present iff :mode is :migration or :joining }
```

The importer's `start-t` is derived at runtime from the highest `t` in `TxSuccess` (the latest committed transaction on the local node).

| Mode         | Use case                           | FHIR reads | FHIR writes | tx-log catch-up check | Transition to `:live`                  |
|--------------|------------------------------------|------------|-------------|-----------------------|----------------------------------------|
| `:live`      | Normal operation                   | yes        | yes         | yes (normal db sync)  | n/a                                    |
| `:migration` | v1 → v2 migration; operator-tested | yes        | no (`503`)  | skipped               | explicit via `POST /__admin/mode/live` |
| `:joining`   | v2 cluster join / rejoin           | no (`503`) | no (`503`)  | skipped               | automatic on state-sync completion     |

**Admin API:**

```
GET  /__admin/mode
  → { mode: "live" | "migration" | "joining",
      source?: "<url>",
      imported_t?: <int>,
      source_current_t?: <int>,    // null if source unreachable
      in_sync?: <bool> }           // imported_t == source_current_t

POST /__admin/mode/migration?source=<url>
  Preconditions: mode = :live AND current_t = 0 (empty DB).
  On success: persists source URL, sets :mode = :migration, starts continuous syncing.
  Once-per-DB-lifetime.

POST /__admin/mode/joining?source=<peer-url>
  Preconditions: mode = :live AND current_t = 0 (empty DB) AND the peer's
  source_version (looked up via GET /__admin/state-sync/status on the peer) is 2.
  On success: persists peer URL, sets :mode = :joining, runs state-sync to completion,
  then auto-transitions to :live.

POST /__admin/mode/live[?force=true]
  Preconditions: mode = :migration AND (imported_t == source_current_t OR force=true).
  On success: clears :source, sets :mode = :live.
  `force=true` overrides the in-sync check — used when the source is legitimately
  unreachable (decommissioned, network partition with operator-confirmed safety).
  Note: this endpoint applies to :migration only. :joining transitions to :live on its own.
```

**Per-mode behavior:**

- **`:live`** — accepts FHIR reads and writes normally. In a v2 cluster, normal db sync (tx-log tailing with current-t checks) is active.
- **`:migration`** — continuously syncs from the persisted source (issuing `GET /__admin/state-sync?start-t=<imported_t>`, resuming on connection drop). `source_current_t` is updated from each stream's header frame. When `imported_t == source_current_t`, sync idles and polls periodically. FHIR reads allowed (operator validation); writes refused. Transition to `:live` is explicit (operator-driven, with the precondition check).
- **`:joining`** — same continuous-sync mechanism, but reads and writes are both refused while the node catches up (partial data must not be served to clients). After each state-sync call ends (`end` frame received with `end_t`), the node decides whether to transition to `:live` or run another sync, based on two checks:

  1. **Retention safety** (mandatory): `end_t + 1` must still be within the cluster's tx-log retention window — i.e. the tx-log adapter's earliest available offset corresponds to a transaction at or before `end_t + 1`. If not, the node cannot tail; it must run another state-sync.
  2. **Gap-shrink efficiency**: the node tracks `gap = current_t - end_t` after each iteration (using the next sync's header frame for a fresh `current_t`). If `gap_new >= gap_prev * R` — i.e. the previous iteration failed to shrink the gap by more than `(1 - R)` — the marginal sync is no longer worth its cost relative to tail-replay; the node transitions to `:live`. Otherwise the gap is still shrinking meaningfully and the node runs another sync. The first iteration always runs at least one follow-up (no prior gap to compare). `gap_new == 0` transitions immediately.

  `R` is a configurable threshold (default `0.5`; sensible range `[0.3, 0.9]`). The default says "keep syncing while the gap is at least halving each pass." Tunable per deployment based on the cost ratio of tx-log replay (full apply path: rewrite stage, conditional refs/updates, internal-id allocation, indexing) versus state-sync (mostly transcription); heavier conditional/reference traffic argues for a smaller `R`, simpler traffic for a larger one.

  When both checks favour transition, the node atomically sets `:mode = :live`, clears `:source`, and begins tx-log tailing from `end_t + 1`. The retention check is load-bearing because letting `:joining` exit on an `end_t` past the tx-log retention would make the node permanently broken (it could neither tail nor re-sync from where it left off).
- **`GET /__admin/mode`** uses the cached `source_current_t` from the most recent stream header. If stale, the handler calls `GET /__admin/state-sync/status` on the source to refresh.
- **Restarts** in `:migration` or `:joining` resume from the persisted source automatically.

### Operator flow

- **v1 → v2 migration** (`docs/deployment/migration-v1-to-v2.md`):
  1. Start a fresh v2 node (empty DB).
  2. `POST /__admin/mode/migration?source=https://v1/fhir`. v2 begins syncing.
  3. v2 continuously syncs. Operator polls `GET /__admin/mode` to monitor progress; reads from v2 freely (test queries, disk-usage checks, etc.). v1 keeps serving production traffic.
  4. When ready to cut over: stop writes to v1 by whatever means appropriate (load-balancer change, shutdown, or — if/when v1 gains it — an explicit v1 read-only flag).
  5. Wait for `in_sync = true` on v2's mode endpoint.
  6. `POST /__admin/mode/live`. Precondition check (`imported_t == source_current_t`) succeeds; node transitions to live.
  7. Point client traffic at v2.
  Verify counts via `TypeStats` / `SystemStats` and a sampled `_history` audit before cutover.
- **v2 node join / rejoin**: start the new node (empty DB); `POST /__admin/mode/joining?source=https://peer/fhir`. The node syncs from the peer (peer serves directly off the live DB with native internal ids on the wire — no minting). The node refuses reads and writes throughout. On reaching `in_sync = true`, the node auto-transitions to `:live` and begins normal db sync from the cluster's tx log.

### Versioning and forward compatibility

The stream's header frame carries a single `version` integer. v1 servers emit `version: 1`; v2 servers emit `version: 2`; future server releases can introduce new versions by bumping it. The importer dispatches on `version` once per stream and runs the corresponding data-frame schema for the rest of the stream; v1 servers never need to read frames.

### Module layout

- New module `modules/db-state-sync/` — protocol definitions, codec, state-sync server handler, client (both source variants).
- v1 ships a release with the state-sync server handler back-ported; no other v1 changes.
- v2 includes the state-sync server handler and the client. The client-side minting algorithm shares its canonical-sort implementation with v2's apply-time rewrite stage.

## Spec, Anomaly, Component Conventions

- Every new public function has a spec in the corresponding `*-spec` namespace under `test/` (per AGENTS.md).
- Allocation overflow (counter ≥ 2^20 in one transaction; `t ≥ 2^44`; `num-changes ≥ 2^32` on one resource) returns an `:cognitect.anomalies/conflict` anomaly; the transaction is recorded in `TxError` and rejected. Not an exception.
- The internal-id allocator is an Integrant component owned by the node; `ig/init-key` produces it from the node's RocksDB handle on `LogicalIdToInternalId`. `m/pre-init-spec` validates dependencies.
- `BlobStore` is an Integrant component, optional. `ig/init-key` produces a `BlobStore` implementation from configuration; absence is a valid state (transport size limit then bounds submission size).
- `set! *warn-on-reflection* true` in every new namespace that touches `java.nio.ByteBuffer` / `ByteString.Builder`.

## Build Sequence

Suggested order so each step lands behind a green test suite before the next is touched:

1. **Internal id codec + mapping CFs** (`codec/internal_id.clj`, `index/logical_to_internal.clj`, `index/internal_to_logical.clj`). Forward and reverse mapping written as a symmetric pair. Standalone unit tests. No callers yet.
2. **Per-transaction allocator** as an Integrant component. Deterministic allocation test against a synthetic transaction sequence.
3. **ResourceAsOf key change** (logical-id → internal-id, drop content-hash from value) and **resource bodies into Index DB as `Resource` CF** keyed `(internal-id (8), num-changes (4))`, replacing the standalone RocksDB resource store. zstd + trained dictionary; CF block cache disabled. Materialization API takes the materialization context; resource cache holds the post-materialization FHIR. Migrate one read path at a time.
4. **Index key changes** in `TypeAsOf`, `SystemAsOf`, `PatientLastChange`, `SearchParamValueResource`, `ResourceSearchParamValue`, `Compartment*`. One index at a time, each with its own test pass.
5. **New reference CFs** `ResourceReference` (forward) and `ReferenceResource` (reverse). Reference-typed search params stop writing to `SearchParamValueResource`/`ResourceSearchParamValue` and write to these instead; chained search + `_include`/`_revinclude` switch readers.
6. **Search/query path** (`resource_handle.clj`, `search_param/*`, `query/*`, `interaction/search/include.clj`) — adapt to internal ids; keep the planner unchanged.
7. **Body shipping — `BlobStore` protocol + S3 reference impl.** Standalone unit tests with a fake / MinIO docker dependency.
8. **Body shipping — submitter-side inline/spill algorithm (distributed storage variant).** Pure-function tests over synthesised body sets; verify (i) all-inline path when under threshold, (ii) correct spill of overflow under threshold, (iii) rejection without `BlobStore` and overflow, (iv) idempotency of repeated submissions (same blobs land under same keys). The standalone variant is exercised separately and bypasses this algorithm entirely.
9. **Body shipping — Kafka transport rewrite.** Single-topic `blaze-tx`; producer queries `max.message.bytes` at startup. Remove all `blaze.bodies` topic plumbing if any was scaffolded.
10. **Body shipping — apply-side parallel fetch + verify.** Hash mismatch → fail transaction; blob store down → retry with backoff.
11. **Standalone in-memory tx-log.** Replace local RocksDB tx-log CF with in-memory ring buffer for subscription consumers. Apply path keeps the three-layer `WriteBatch` structure (parallel per-resource batches now including the `Resource` CF body → tx-index batch → `TxSuccess` batch); the only thing the standalone change collapses is the tx-log persistence step itself. Remove tx-log replay code paths. Standalone now runs a single RocksDB instance (Index DB only).
12. **Rewrite stage** — bundle-internal placeholder resolution moved out of the interaction layer; conditional reference resolution; conditional update target resolution. Conditional refs and conditional updates are new FHIR capabilities and get their own behavioral test files.
13. **Cassandra resource store removal** — delete `db-resource-store-cassandra`; update build matrix.
14. **State sync** — new `modules/db-state-sync/` with protocol, codec, server handler, client. v2 implements server (transcriptive) + client (transcriptive). v1 ships a back-ported server-only release including the scratch-CF minting algorithm. Importer runs index rebuild pass after stream end. Operator runbook docs.
15. **Docs + perf benchmarks** — update `docs/implementation/database.md` and `docs/deployment/environment-variables.md`; run microbenchmarks against v1 baseline.

## Verification

End-to-end tests in `modules/db/test/` (and `modules/interaction/test/` for FHIR-surface behavior):

1. **Internal id allocation** — given a sequence of synthetic transactions including resources, references to existing resources, references to non-existing resources (ref-integrity off), bundle-internal placeholders, conditional refs, and conditional updates, assert: (a) the same internal id is assigned to every reference to the same logical id, (b) phantom ids minted on first reference are reused when the resource is later created, (c) `patient-purge` and `delete-history` do not evict entries from either `LogicalIdToInternalId` or `InternalIdToLogicalId`, (d) forward and reverse mappings are consistent for every allocated id.
1a. **`_history` rendering survives purge** — delete a resource, then `delete-history` its create/put versions; assert the surviving delete tombstone still renders as `Type/id` in `_history` (logical id read from `InternalIdToLogicalId`, not from any body). Also: a resource whose only version is a delete renders correctly.
2. **Determinism** — replay the same tx log on two fresh nodes; byte-compare RocksDB snapshots (per CF) at every `t`. Must match.
3. **Conditional reference resolution** — bundle that references `Patient?identifier=X` where 0, 1, and 2 matches exist; assert success only for the 1-match case and `TxError` otherwise.
4. **Conditional update** — same matrix; assert that a 1-match resolves to a `put` (preserving internal id and incrementing `num-changes`) and a 0-match resolves to a `create` (new internal id).
5. **Inline/spill correctness (distributed storage variant)** — given a synthesised submission with bodies of varied sizes, assert: (a) all bodies inlined when total ≤ threshold, (b) overflow correctly identified and spilled when blob store present, (c) submission rejected upfront when overflow exists without a blob store, (d) sort-ascending-by-size produces the maximum-inlined-count solution under the threshold.
6. **Kafka message size discovery** — apply node configured with a low broker `max.message.bytes`; verify submitter respects it; verify a runtime config change triggers a refresh on next publish error.
7. **Standalone durability under crash** — inject crashes at various points (mid-submit, mid-rewrite, mid-`WriteBatch`, post-commit). Assert: client either gets success and the tx is persisted, or never gets success and there's nothing to recover.
8. **Subscription resync after ring overflow** — slow subscriber in standalone falls behind the in-memory ring; assert it receives a resync signal and can replay forward from `_since`.
9. **Blob store integrity** — tamper with a blob in-place; apply node detects sha256 mismatch on fetch and fails the transaction.
10. **Blob store outage** — block blob-store access during apply; apply loop blocks at the offending commands offset and retries with backoff; recovery resumes cleanly after the blob store returns.
11. **State sync — v1 → v2 migration, bit-identical to v2-native.** Stand up a v1 instance, drive a curated transaction sequence (mixed create/put/delete/conditional/refs/purge). Separately, replay the **same** transaction sequence through a fresh v2 cluster end-to-end. Then state-sync the v1 instance into a second fresh v2 cluster. Assert: every CF (`LogicalIdToInternalId`, `InternalIdToLogicalId`, `ResourceAsOf`, `TypeAsOf`, `SystemAsOf`, search/reference CFs, `Compartment*`, `TypeStats`, `SystemStats`) is byte-identical between the two v2 clusters. This proves the client-side minting algorithm produces exactly the same internal ids as live v2 apply.
12. **State sync — full history preserved.** v1 → v2 migration on a dataset with multiple versions per resource (including deletes and purges). Assert: `_history` (instance, type, system) on v2 returns the same versions in the same order as v1; `vread` for every version succeeds; tombstone metadata is preserved; purged-version metadata rows are present with bodies absent.
13. **State sync — v2 late join.** Three-node v2 cluster; write traffic until the tx-log retention window has rolled past the start; add a fourth node; verify it pulls state from a peer (peer transcribes native internal ids, no scratch CF) and converges to byte-identical CF snapshots as its peers.
14. **State sync — resumability and idempotency.** Interrupt the stream mid-import; reissue `GET ?start-t=<saved>`; verify that re-streamed frames at the transaction boundary write idempotently and the run converges to the same final state as an uninterrupted run. Exercise both source variants.
14a. **State sync — concurrent purge + tx-log catch-up.** Start a sync. While in flight, on the source run a `delete-history` / `patient-purge` whose transaction commits after the iterator's snapshot — its effect (`purged-at?` on the row, body deletion) is invisible to the stream. After `end`, the client tails the tx log from `t_end + 1`, picks up the purge transaction, and applies it locally. Assert final client state matches live source state.
14b. **State sync — source restart mid-stream.** Restart the source process mid-stream. The client retries `GET ?start-t=<saved>`; the source serves a fresh iterator. Assert convergence with an uninterrupted run.
14c. **State sync — client-side mint determinism (v1 source).** Drive a v1 source through a curated transaction sequence (mixed create/put/delete/conditional/refs across types, including same-transaction mutual references and references to absent targets under ref-integrity-off). Run state-sync into two fresh, independent v2 nodes. Assert every CF — including `LogicalIdToInternalId`, `InternalIdToLogicalId`, `ResourceAsOf`, `TypeAsOf`, `SystemAsOf`, the search/reference CFs, `Compartment*`, `TypeStats`, `SystemStats` — is byte-identical between the two v2 nodes. This proves the per-transaction canonical sort produces deterministic ids regardless of which client computes them.
14d. **State sync — client-side mint vs. live v2 apply (v1 source).** Replay the same transaction sequence used in 14c through a fresh v2 cluster end-to-end (live apply path). Compare its final CFs against a v2 node populated by state-sync from a v1 source running the same sequence. Assert byte-identity. This is the load-bearing equivalence: client-side mint must produce the same ids the live v2 apply would have minted.
14e. **State sync — body availability invariant.** Verify v1 never deletes bodies under any operation (including `delete` tombstones, which simply have no body) — needed by the mint's reference-collection pass. Construct a v1 source containing every operation kind v1 supports; assert every create/put version has its body still present.
15. **State sync — per-transaction buffering bound.** Verify the v1-source path holds at most one transaction in the importer at a time. Drive a sequence containing one very large transaction (e.g. a 100 k-resource bundle) followed by many small transactions; assert peak importer memory tracks the large transaction's body footprint, not the cumulative stream.
15a. **Node mode lifecycle.** Cover the state machine: (a) fresh node starts in `:live`; (b) `POST /__admin/mode/migration?source=...` on a live empty node succeeds, refuses subsequent FHIR writes (`503`) and accepts reads; (c) the same call on a non-empty live node returns `409`; (d) a `:migration` node restart resumes migration without operator action; (e) `POST /__admin/mode/live` while `imported_t < source_current_t` returns `409`; (f) the same call with `force=true` succeeds; (g) on success the source is cleared and writes are accepted; (h) re-entering migration on the same DB returns `409` (one-way).
15b. **Joining mode lifecycle.** (a) `POST /__admin/mode/joining?source=...` against a v2 peer on a live empty node succeeds, refuses both reads and writes (`503`) throughout; (b) restart in `:joining` resumes from the persisted source; (c) when a state-sync ends with `end_t + 1` inside tx-log retention AND the gap-shrink ratio `gap_new / gap_prev >= R` (default `R = 0.5`), the node atomically transitions to `:live`, clears `:source`, and begins tx-log tailing from `end_t + 1`; (d) if `end_t + 1` is past retention, the node stays in `:joining` and runs another sync regardless of shrink ratio; (e) if retention is satisfied but the gap is still shrinking faster than `R`, the node runs another sync to keep state-sync's cheaper transcription advantage; (f) the first iteration always runs at least one follow-up sync (no prior gap to measure); (g) `POST /__admin/mode/joining` on a non-empty live node returns `409`; (h) `POST /__admin/mode/joining` against a v1 source returns `409`.
16. **Microbenchmarks** — `modules/db/perf/` (extend existing): index size on disk, scan throughput for `SearchParamValueResource`, point-read latency for `ResourceAsOf` and the resource store. Baseline against v1 on the same Synthea dataset. Target: ≥ 30% reduction in total RocksDB on-disk size for SHA-256-hex id deployments; ≥ 20% improvement in point-read latency.
17. **Coverage** — `make test-coverage` ≥ 95% forms across changed modules, per project convention.

Standard CI gates: `make fmt`, `make lint`, `make -C modules/db test`, `make -C modules/interaction test`, `make -C modules/db-blob-store test`, `make -C modules/db-blob-store-s3 test` (with MinIO docker service), `make -C modules/db-state-sync test`. Module-spanning runs go through `make test`.

## Open items (deferred)

- **RocksDB value-size limits for the `Resource` CF.** What happens when a single rewritten body (e.g., a `DocumentReference` with embedded base64 multi-MB payload) exceeds healthy RocksDB value sizes. Candidates: RocksDB BlobDB on the `Resource` CF, a body-specific storage-time spill (distinct mechanism from transport-time spill), or operator-enforced per-resource size caps. Discussed separately.
- **Blob-store janitor.** Active deletion of applied blobs based on Kafka consumer-group offsets, as an alternative to time-based retention. Future optimization.
- **2-byte densely-assigned `c-hash` for search parameter codes** (analogous to the tid table). Smaller win than the type-id change; deferred.
- **State sync — `prepare-state-sync` pre-build on v1.** Optional operator command that materialises a permanent `LogicalIdToInternalId` CF on v1 ahead of cutover, shortening the freeze window. Same algorithm as the inline scratch CF; just moved earlier. Worth implementing if the freeze window matters for very large deployments.
- **State sync — `--history=live-only` opt-out.** A future opt-in flag that streams only the live tip (no history) for research/non-conformant deployments that don't use the FHIR history API and want a faster bootstrap. Default remains full-history.

## See Also

- [Patient Sharding](v2-patient-sharding.md) — cluster topology, per-version placement rule, coordinator/owner read pipeline, shard moves via state-sync per shard, CQL fan-out. Built on top of this storage layer; `N=1` is the degenerate single-shard case used by the standalone variant.
