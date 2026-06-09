# Blaze v2 — Patient Sharding

## Context

The v2 [Core Storage Redesign](v2-core-storage-redesign.md) treats every node as holding the complete database. Even in the distributed-storage variant, every node applies every transaction in full and indexes every resource. That model bounds a deployment's resource count by what fits on one machine, and it caps CQL aggregate throughput at one JVM's parallelism budget regardless of cluster size.

Patient sharding partitions the heavy data (bodies and most indices) across nodes by patient compartment, so each node holds a slice of the cluster's resources and can answer compartment-scoped queries — `Patient/{X}/$everything`, FHIR compartment search, CQL `Patient`-context evaluation — without leaving the node. CQL aggregate queries that walk a patient population fan out across shards and run in parallel; the coordinator joins partial results. Storage capacity scales with node count for the heavy data; metadata and identity stay globally replicated so routing, identity, and version-at-`t` lookups remain single-CF reads on any node.

The design is layered on top of v2's storage decisions and adds no requirements that v2 didn't already accommodate. v2's invariants — global `t` from a single-partition tx-log, deterministic internal-id minting via canonical sort, `Resource` CF inside the index DB, content-hash-free addressing — carry over unchanged. Sharding adds a placement dimension to the storage layer and a coordination layer on top.

### Goals

- Each node holds only a partition of `:patient-scoped` data; the cluster's bodies and search/compartment/reference indices are split by patient compartment.
- Compartment-scoped reads (`Patient/{X}/...`, single-patient CQL evaluation) run fully on one shard with no inter-node RPC for the query itself.
- CQL aggregate queries over a patient population evaluate per-shard in parallel; coordinator aggregates partial results.
- Replication factor `R ≥ 1` per shard; loss of a single node never loses a shard.
- Online shard moves between existing nodes; shard count is fixed at deploy.
- v2's global transaction order, atomic cross-compartment transactions, and deterministic apply across nodes are preserved.

### Non-goals

- Horizontal write-throughput scaling. The single-partition tx-log remains the apply ceiling.
- Sharding the tx-log itself.
- Cross-datacenter / geo-replication (out of scope; can be layered later).
- Online change of the total shard count `N` (would require re-minting; deliberately excluded).
- Rack-aware / zone-aware replica placement in v1 (deferred; see [Open Items](#open-items-deferred)).

## Topology

A v2-sharded cluster is parameterised by three numbers:

- **`M`** — number of physical nodes. Each node runs a full v2 server process.
- **`N`** — number of logical shards, fixed at cluster creation. `N` is an operator choice; the suggested defaults are `N=1` for the standalone variant and `N=256` for the distributed variant. `N` is never changed after deploy — that constraint is what lets the storage layer treat per-version `shard-id` placement as immutable and what lets internal-id minting stay deterministic without re-mapping.
- **`R`** — replication factor (default `2`). Each shard is owned by exactly `R` distinct nodes. The topology must satisfy `M ≥ R`.

Sharding is meaningful when `M > R`; at `M = R` every shard lives on every node and the deployment is functionally equivalent to a single-instance v2 with replication. The standalone variant uses `M = 1, R = 1, N ∈ {1, 256}` — see [Standalone](#standalone).

Every node has two classes of column-family data:

| Class               | Definition                                                                                 | Storage cost per node                                      |
|---------------------|--------------------------------------------------------------------------------------------|------------------------------------------------------------|
| Sharded             | Per-shard data (bodies + per-version sharded indices) for the shards this node owns        | `~(R/M)` of cluster heavy data                             |
| Globally replicated | Identity, version metadata, transaction success, cluster config — everything on every node | Full content; ~100–150 GB for billion-resource deployments |

Constants like the `tid` table, the placement table, and the trained zstd dictionary live in source code or as build artifacts, not in any CF — see [Critical Files](#critical-files). The complete CF placement matrix is in [CF Placement](#cf-placement).

There is no dedicated coordinator role. Any node accepts FHIR requests over the public port and acts as coordinator for that request; the load balancer can spread incoming traffic uniformly without shard-awareness. Inter-node traffic runs on a separate [cluster channel](#cluster-channel) with its own port and security policy.

The partition→node assignment lives in a globally replicated `ClusterConfig` CF (see [Cluster Operations](#cluster-operations)).

## Placement

Every resource type is classified once, from FHIR's `CompartmentDefinition/patient` augmented with operational judgement on multi-compartment-by-design types. The classification lives as a static table in `modules/db/src/blaze/db/impl/codec/placement.clj`, committed alongside the type table.

| Class                | Examples                                                                                                                                                               | Placement rule                                                           |
|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `:patient-root`      | `Patient`                                                                                                                                                              | `shard(this-version.logical-id)` — the patient is in its own compartment |
| `:patient-scoped`    | `Observation`, `Condition`, `Encounter`, `MedicationRequest`, `Procedure`, ...                                                                                         | `shard(primary-subject.logical-id)` from this version's body             |
| `:global-replicated` | `Practitioner`, `Organization`, `Medication`, `ValueSet`, `CodeSystem`, `Library`, `Measure`, `Communication`, `AuditEvent`, `Provenance`, `Composition`, `Group`, ... | Every node                                                               |

`:global-replicated` covers two distinct cases:

- **Reference data types** (Practitioner, Organization, Medication, ValueSet, ...) — small in volume, referenced by every clinical resource, kept everywhere so reference rendering is always a local lookup.
- **Multi-compartment-by-design types** (Communication, AuditEvent, Provenance, Composition, ...) — types whose patient compartment definition resolves through multiple peer reference paths that routinely point at different patients. Replicating these everywhere avoids the per-instance shard-set complexity of trying to place them on exactly the involved compartments.

### Per-version placement

Placement is per-version, not per-resource. For each new version `v_n` of a resource, the rewrite stage computes:

```
case type-class of
  :global-replicated:                            shard-id = 0xFFFF (sentinel for "everywhere")
  :patient-root:                                 shard-id = shard(v_n.logical-id)
  :patient-scoped, subject resolves to Patient:  shard-id = shard(v_n.subject.logical-id)
  :patient-scoped, subject unresolvable:         shard-id = 0xFFFF
```

The shard function is `xxhash64(logical-id-bytes) mod N` — a fast, well-distributed mix; not a cryptographic hash. The input is the *logical* id rather than the internal id because routing decisions at HTTP-request time must be resolvable before any DB read, and the client supplies the logical id.

The shard-id is recorded in the version's `ResourceAsOf` row (see [CF Placement](#cf-placement)). Subsequent versions of the same resource may carry different shard-ids — a subject change or a `Patient/$merge` re-subjects later versions, and those versions land on the new shard while earlier versions remain on the original. Internal-id identity is stable across versions; placement is not.

### Why per-version and not per-resource

Per-resource pinning (decide the shard at first allocation, freeze it) was considered and rejected for two reasons:

- **`Patient/$merge` is a first-class FHIR operation** that re-subjects every clinical resource in the source patient's compartment to the target patient. Pinning would either reject these writes or leave the merged resources stranded on the source patient's shard, violating compartment locality for the target patient's compartment query.
- **Primary-compartment locality is a structural invariant under per-version placement.** For a version `v_n` whose primary subject is patient `X`, the body and `X`'s compartment-index entry are written to `shard(X)` in the same per-resource WriteBatch — so an index entry on `shard(X)` for `X`'s compartment always finds its body locally. Pinning would break this: after a subject change, the body could stay on `shard(A)` while the new version belongs in patient `B`'s compartment on `shard(B)`, forcing every read of that resource through `B`'s compartment into a cross-shard body fetch. Per-version placement keeps the *primary* compartment query path fully local on the owner shard; the secondary-membership compartments (e.g. `performer = Patient/Y` where `Y ≠ subject`) still cross shards for body fetches, but that's the rare case rather than the rule. See [Multi-compartment membership within one version](#multi-compartment-membership-within-one-version).

### Multi-compartment membership within one version

A `:patient-scoped` version can be a compartment member of more than one patient — e.g. an `Observation` with `subject = Patient/A` and `performer = Patient/Y` is in both `A`'s and `Y`'s compartment per FHIR. Under the placement rule above, the version's body lives on `shard(A)` (the primary subject). The compartment indices on `shard(Y)` still hold entries for this version, written by the apply on `Y`'s owner — but a compartment query against `shard(Y)` that materialises this resource will perform a cross-shard body fetch from `shard(A)`. See [Read Pipeline](#read-pipeline).

This is a deliberate trade-off: optimise the common case (primary-subject compartment, fully local) and pay a cross-shard hop for the rare secondary-membership case rather than replicate bodies to every member shard.

### Subject changes and `Patient/$merge`

A subject change from `A` to `B` at version `v_n`:

- v_{n-1} stays on `shard(A)` (its `ResourceAsOf.shard-id = shard(A)`); its body and indices are on `shard(A)`.
- v_n lands on `shard(B)`; its body and indices are on `shard(B)`.
- v_n's compartment-B index entry is on `shard(B)`. No v_n compartment-A index entry is written (the resource isn't in A's compartment anymore).
- v_{n-1}'s compartment-A index entry remains on `shard(A)` as a historical row. A compartment-A query at `t ≥ T.t` (the change transaction) walks the local index, finds this row with `num-changes = n-1`, validates against the globally-replicated `ResourceAsOf` at query `t` which returns `num-changes = n`, observes the mismatch, and discards it. The same `num-changes` equality filter that v2 uses to discard stale search-param entries discards stale compartment entries — sharding adds no new read-time logic.

`Patient/$merge` is the same mechanism repeated for every resource in the source compartment: each gets a new version written to `shard(target-patient)`, while the source's historical versions remain on `shard(source-patient)`.

### Phantom-then-create

The mapping CFs (`LogicalIdToInternalId`, `InternalIdToLogicalId`) are unchanged from v2 — append-only, no `shard-id`. They're written at first allocation (phantom or real). `ResourceAsOf` is written at first real create; the shard is computed at that point. A `:patient-scoped` phantom whose real create later lacks a resolvable subject lands as `shard-id = 0xFFFF` (globally replicated) for its first version; later versions follow their own subject. No special phantom-shard handling.

## CF Placement

Every CF is one of two placement classes. The class determines where rows physically live and whether keys carry a `shard-id` prefix.

| Class       | Stored where                      | Key prefix                            | Apply pattern                                                    |
|-------------|-----------------------------------|---------------------------------------|------------------------------------------------------------------|
| **Global**  | Every node, full content          | No `shard-id`                         | Layer 2 (tx-index) on every node; identical content cluster-wide |
| **Sharded** | Owners of the relevant shard only | 2-byte `shard-id` prefix on every key | Layer 1 (per-resource) only on the owning node(s)                |

### CF Layout

The v2 layout adapted for sharding:

| CF                                    | Class   | Key                                                                                                              | Value                                                            |
|---------------------------------------|---------|------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `LogicalIdToInternalId`               | Global  | `tid (2), logical-id (≤64)`                                                                                      | `internal-id (8)`                                                |
| `InternalIdToLogicalId`               | Global  | `internal-id (8)`                                                                                                | `tid (2), logical-id (≤64)`                                      |
| `ResourceAsOf`                        | Global  | `internal-id (8), t-desc (6)`                                                                                    | `tid (2), num-changes (4), op (1), shard-id (2), purged-at? (6)` |
| `TypeAsOf`                            | Global  | `tid (2), t-desc (6), internal-id (8)`                                                                           | `num-changes (4), op (1), shard-id (2), purged-at? (6)`          |
| `SystemAsOf`                          | Global  | `t-desc (6), tid (2), internal-id (8)`                                                                           | `num-changes (4), op (1), shard-id (2), purged-at? (6)`          |
| `TxSuccess`                           | Global  | `t (6)`                                                                                                          | tx metadata (instant)                                            |
| `TxError`                             | Global  | `t (6)`                                                                                                          | error details                                                    |
| `TByInstant`                          | Global  | `instant`                                                                                                        | `t (6)`                                                          |
| `TypeStats`                           | Global  | `tid (2), t (6)`                                                                                                 | total, num-changes                                               |
| `SystemStats`                         | Global  | `t (6)`                                                                                                          | total, num-changes                                               |
| `ClusterConfig` (new)                 | Global  | config-key                                                                                                       | partition→node assignment + version counter; node registry; mode |
| `Resource`                            | Sharded | `shard-id (2), internal-id (8), num-changes (4)`                                                                 | CBOR body (v2 transforms: elision + internal-id refs)            |
| `SearchParamValueResource`            | Sharded | `shard-id (2), c-hash (4), tid (2), value (var), internal-id (8), num-changes (4)`                               | empty                                                            |
| `ResourceSearchParamValue`            | Sharded | `shard-id (2), tid (2), internal-id (8), num-changes (4), c-hash (4), value (var)`                               | empty                                                            |
| `CompartmentResource`                 | Sharded | `shard-id (2), c-hash (4), comp-internal-id (8), tid (2), internal-id (8)`                                       | empty                                                            |
| `CompartmentSearchParamValueResource` | Sharded | `shard-id (2), c-hash (4), comp-internal-id (8), sp (4), tid (2), value (var), internal-id (8), num-changes (4)` | empty                                                            |
| `PatientLastChange`                   | Sharded | `shard-id (2), patient-internal-id (8), t-desc (6)`                                                              | empty                                                            |
| `ResourceReference` (forward)         | Sharded | `shard-id (2), internal-id (8), num-changes (4), c-hash (4), target-tid (2), target-internal-id (8)`             | empty                                                            |
| `ReferenceResource` (reverse)         | Sharded | `shard-id (2), target-internal-id (8), c-hash (4), tid (2), internal-id (8), num-changes (4)`                    | empty                                                            |

What `shard-id` anchors per sharded CF:

| CF                                                                                      | `shard-id` anchor                                                                                                        |
|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `Resource`, `SearchParamValueResource`, `ResourceSearchParamValue`, `ResourceReference` | The version's owner (`shard(this-version.primary-subject)` per the placement rule)                                       |
| `CompartmentResource`, `CompartmentSearchParamValueResource`                            | The compartment patient's shard. A version that's a member of K patient compartments writes K rows, one per member shard |
| `PatientLastChange`                                                                     | The patient's own shard                                                                                                  |
| `ReferenceResource`                                                                     | The target's owner shard                                                                                                 |

### Why the shard-id prefix

Operational: shard moves and per-shard state-sync need to identify a shard's rows as a contiguous range. With the prefix:

- Moving shard 42 = range scan on `[42, 43)` for each sharded CF, stream to target, `RangeDelete([42, 43))` to clean up. Linear in shard 42's data, contiguous on disk.
- Without the prefix, internal-ids from different shards interleave (the shard hash distributes uniformly across the internal-id keyspace). Moving a shard would require full-CF iteration plus per-row shard lookup.

The shard-id width is 2 bytes for future-proofing (max `N = 65535`); v1 deployments use up to 1024 shards in practice. RocksDB prefix-shared block encoding amortises most of the 2-byte cost across rows of the same shard; realistic SST inflation is ≤ 1%.

### Per-row size delta vs unsharded v2

Sharded CFs each gain 2 bytes per key. Global `*AsOf` CFs each gain 2 bytes per value. See the [v2 CF size table](v2-core-storage-redesign.md#index-layout-changes-v2) for the unsharded baseline; the delta is uniformly +2 bytes per affected row, well within RocksDB prefix-share gains.

## Cluster Channel

Inter-node communication runs on a dedicated port, separate from the public FHIR REST port. The split exists for four reasons: a different security model (mTLS between nodes vs. application auth for clients), different protocol semantics (streaming sub-queries, range scans), demand-driven streaming with explicit cancellation, and isolation from public traffic spikes.

### Transport

- New module `modules/cluster-server/`: an HTTP/2 server bound to the cluster address.
- New module `modules/cluster-client/`: an HTTP/2 client used by every node when acting as coordinator.

Both sit directly on Jetty 12's HTTP/2 stack. Three HTTP/2 properties carry the design:

- **Multiplexing** — a node owning K shards receives one sub-query over a single connection, and the coordinator fans out to M nodes over M multiplexed streams, not N. Dispatch is per-node, not per-shard.
- **Flow control** — the per-stream window is the back-pressure signal that throttles each owner's scan to the coordinator's draw.
- **`RST_STREAM`** — the coordinator's cancellation signal when a page fills.

Responses stream: the owner emits materialized resources as the coordinator pulls, never buffering a whole page. The response body is a CBOR sequence (`application/cbor-seq`, RFC 8742) — the same framing state-sync uses — in which each resource is one self-delimiting CBOR data item. The owner writes (and flushes) one item per resource; HTTP/2 DATA frames sit below this and need not align with item boundaries, so the decoder recovers items from the byte stream regardless of how the transport chunks or coalesces them.

### Streaming index scans to the async sink

The owner's data source is a set of RocksDB iterators: synchronous, pull-only, and not thread-safe — an iterator with its snapshot and `ReadOptions` must be opened, advanced, and closed on a single thread, and materializing each result does further point reads (`ResourceAsOf`, `InternalIdToLogicalId`). Jetty 12's write side is the inverse: `Response` is a `Content.Sink` whose `write(boolean last, ByteBuffer, Callback)` admits one write at a time and signals readiness for the next through the callback, with HTTP/2 flow control delaying that callback until the bytes are flushed.

The two are bridged with `java.util.concurrent.Flow` (`blaze.async.flow`), whose `request(n)` demand protocol is exactly Jetty's demand model:

- **Scan side — a demand-driven `Flow$Publisher`.** Each owned shard's scan is a pull-based publisher backed by one producer thread on a dedicated query executor. That thread owns the iterator and snapshot for the publisher's lifetime. `request(n)` advances the iterator up to n times, materializes each version to canonical FHIR, CBOR-encodes it, and emits it via `onNext`. Materialization runs on the producer thread, so the publisher yields ready-to-write `ByteBuffer`s and nothing is produced ahead of demand. `cancel()` — and normal end-of-scan — closes the iterator and snapshot in a `finally` on that same thread.
- **Sink side — a `Flow$Subscriber` over `Content.Sink`.** `onNext(buf)` issues `write(false, buf, cb)`; `cb.succeeded` calls `request(1)`; `cb.failed` (RST_STREAM or idle timeout) calls `subscription.cancel()`; `onComplete` issues the terminal `write(true, EMPTY)`. Requesting one item per flushed write enforces Jetty's single-in-flight-write rule and makes the back-pressure exact: because the callback is gated by the flow-control window, the iterator advances precisely as fast as the coordinator drains — an owner scans only as far as the coordinator drew.

Cancellation crosses threads safely. The Jetty callback thread only flips demand/cancel state on the subscription; the producer thread observes it and performs all native cleanup itself. No iterator is ever touched — least of all closed — from a Jetty thread.

**Multi-shard fan-in.** A per-node sub-query owning K shards opens K scan publishers, merges them through a k-way merge `Flow$Processor` (demand-propagating: the merge requests across its inputs as the sink requests from it), and feeds one `Content.Sink` subscriber. This is the inner half of the two-level merge in [Scatter-gather reads](#scatter-gather-multi-shard-reads); the per-node streams merge again at the coordinator.

Two general-purpose primitives are added to `blaze.async.flow` for this: a **pull-based publisher** (turns a blocking step + close into a `request(n)`-honoring publisher on a supplied executor) and a **k-way merge processor**. The existing `SubmissionPublisher`-based processors are push/buffered and are not used on the scan path, where eager buffering of materialized resources would both waste heap and defeat the draw-bounded scan.

**Threading.** Blocking scans run on a dedicated query executor, never on Jetty's selector/acceptor threads — those run only the non-blocking write callbacks. Peak scanner threads per node ≈ concurrent sub-queries × owned shards touched; the executor is sized accordingly and bounds in-flight scan concurrency.

### Authentication and configuration

mTLS between nodes, anchored on a cluster-internal CA. Compromise granularity is the whole cluster, which matches the trust model (no per-endpoint scopes; a valid cluster cert grants full intra-cluster privileges).

This mTLS governs the cluster port only. The `/__admin` endpoints stay on the public-facing server under Blaze's existing admin auth (bearer token or mTLS, deployment-dependent), unchanged from non-sharded v2, so external operators, the frontend, and `blazectl` reach them as before.

New environment variables, documented in `docs/deployment/environment-variables.md`:

```
CLUSTER_BIND_ADDR        host:port for the cluster server (default 0.0.0.0:9080)
CLUSTER_TLS_CERT         path to this node's cluster cert
CLUSTER_TLS_KEY          path to this node's cluster key
CLUSTER_TLS_CA           path to the cluster CA cert
CLUSTER_CLIENT_TIMEOUT   client-side per-stream idle timeout (ISO-8601 duration, e.g. PT30S)
```

### Endpoints

Two surfaces. The **cluster port** carries node-to-node RPC only — mTLS, never exposed to clients. The **`/__admin` endpoints stay on the public-facing server**, on the same port and behind the same auth as non-sharded v2, so the frontend and `blazectl` reach them exactly as before; sharding does not move them.

**Cluster port (`CLUSTER_BIND_ADDR`, mTLS, node-to-node):**

| Path                                                 | Method | Purpose                                                                              |
|------------------------------------------------------|--------|--------------------------------------------------------------------------------------|
| `/cluster/query`                                     | POST   | Per-node sub-query: query spec + `shards=…` + pinned `t`. Streaming CBOR response.   |
| `/cluster/body/{internal-id}/{num-changes}`          | GET    | Single materialized resource (for cross-shard `_include` / `_revinclude` follow-ups) |
| `/cluster/include`                                   | POST   | Batched forward reference lookup (`_include` follow-ups)                             |
| `/cluster/rev-include`                               | POST   | Batched reverse reference lookup (`_revinclude` follow-ups)                          |
| `/cluster/cql`                                       | POST   | Per-node CQL sub-query: measure + evaluation params + `shards` + pinned `t`. Single CBOR response (partial MeasureReport). |
| `/cluster/state-sync?shard=<id>&start-t=<int>`       | GET    | Per-shard state-sync source (extends v2's `/__admin/state-sync`)                     |
| `/cluster/state-sync/status?shard=<id>`              | GET    | Source's current `t` for this shard                                                  |

**Public-facing server (operator-facing, same port and auth as non-sharded v2; reachable by the frontend and `blazectl`):**

| Path                                                 | Method | Purpose                                                                              |
|------------------------------------------------------|--------|--------------------------------------------------------------------------------------|
| `/__admin/cluster-config`                            | GET    | Read the current `ClusterConfig`                                                     |
| `/__admin/shard-move`                                | POST   | Propose `{shard, from, to}`; commits a config-change transaction                     |
| `/__admin/shard-move/{shard-id}`                     | GET    | Status of in-flight move                                                             |
| `/__admin/shard-move/{shard-id}`                     | DELETE | Cancel in-flight move                                                                |
| `/__admin/mode/{live\|draining\|joining\|migration}` | POST   | Node mode transitions                                                                |

The v2 admin endpoints — `/__admin/state-sync`, `/__admin/state-sync/status`, `/__admin/mode/...` — remain exactly where non-sharded v2 serves them, on the public-facing server with unchanged auth; sharding does not relocate them. It only adds `/__admin/cluster-config` and `/__admin/shard-move` alongside them and extends `/__admin/mode` with `:draining`. Operator-facing full-node bootstrap (v1→v2 migration, late join) continues to use the public `/__admin/state-sync`; the cluster-port `/cluster/state-sync?shard=` is a separate node-to-node surface used only for shard moves. v1 sources expose `/__admin/state-sync` with full-system frames (degenerate `shard=0`).

### `/cluster/query` request

One POST per dispatched node. The request is a single CBOR map in the body (clauses contain arbitrary strings; nothing rides in the URL):

| Field         | Required | Data Type                  | Description                                                                                                        |
|---------------|----------|----------------------------|--------------------------------------------------------------------------------------------------------------------|
| `tid`         | yes      | number                     | the resource type id from the static type table (both ends compile the same table; no name translation on this channel) |
| `clauses`     | yes      | clauses                    | the parsed query, optional sort clause in first position — see below                                               |
| `shards`      | yes      | shard-id[]                 | the shards this node must scan; may include the `0xFFFF` sentinel ([dispatched to exactly one node](#merge-ordering-and-dedup)) |
| `t`           | yes      | number                     | pinned snapshot `t` ([Read Consistency](#read-consistency))                                                        |
| `compartment` | no       | `[c-hash, internal-id]`    | present for compartment-scoped sub-queries (`Patient/{X}/...`); the query then runs against the compartment indices |
| `cursors`     | no       | map shard-id → internal-id | exclusive per-shard resume positions on continuation (bare start-ids; sort resume bounds are re-derived at the owner — see [Pagination](#pagination))   |

There is no page-size field: the coordinator draws results via HTTP/2 flow control and cancels with RST_STREAM when its page is full, so the owner never needs to know the page size.

`cursors` is a map, not a single start-id, because a per-node sub-query iterates several shards and each shard's stream resumes at its own position.

**Clause format.** `clauses` carries Blaze's parsed clause format — spec `:blaze.db.query/clauses` in `modules/spec/src/blaze/spec.clj`: a vector of disjunctions (AND across the vector), each disjunction a search clause `[code value+]` or a vector of those (OR), with an optional sort clause `[:sort code :asc|:desc]` allowed in first position only (the existing `d/type-query` convention). Values are the FHIR-level value strings (`"ge2020-01-01"`, `"http://loinc.org|8480-6"`); codes may carry modifiers (`"status:not"`). The same CBOR encoding the tx log already uses for the clauses of `conditional-delete` / `conditional-update` applies.

This level — below raw FHIR search parameters, above the compiled query plan — is deliberate:

- **Raw FHIR query parameters** would make every owner re-run the interaction layer. Result-parameter handling (`_count`, `_include`, `_sort` extraction, lenient mode) and parameter validation are coordinator concerns — `_include` follow-ups, paging, and Bundle assembly all live at the coordinator — and parse errors must surface once, pre-dispatch, as a proper `OperationOutcome`, not mid-stream on M nodes.
- **The parsed clause format** is exactly the `d/type-query` API boundary the owner executes anyway, already spec'd, readable in logs, and already a cross-node wire format: v2 tx commands ship search clauses in this same shape through the tx log.
- **The compiled query plan** (resolved search params, compiled values, scan/seek split) is tied to index internals and would couple node versions; it would also pre-empt the owner's planning freedom — each owner chooses its execution (including scan-sorted vs. buffered top-K, per [Core Storage › Sorting](v2-core-storage-redesign.md#sorting)) from its own local statistics.

The coordinator parses and validates once, rejecting invalid parameters before any dispatch; each owner compiles and plans locally.

### `/cluster/query` response

`200 OK`, `Content-Type: application/cbor-seq`, streamed. The stream follows the same discipline as state-sync: one header frame, then result frames, then a terminal frame.

```
header := { declined?:      map shard-id → node-id[],   // shards not read-owned here, with their current owners
            config-version: int }                        // advisory ClusterConfig.version refresh hint
result := { shard:       int,                            // owning shard of this result
            id:          internal-id (8),
            num-changes: int,                            // the matched (current-at-t) version
            sort-value:  value,                          // present iff the query sorts
            resource:    bytes }                         // materialized FHIR, internal CBOR
end    := { end: true }
error  := { error: { category, message } }
```

- **`header`** comes first so the coordinator acts on it before any results arrive: `declined` is the [routing-validation](#routing-validation) outcome — the shards this node no longer read-owns, each with the owners it currently sees — letting the coordinator re-dispatch them in parallel with consuming this stream. An empty/absent `declined` means all requested shards are served.
- **`result`** frames arrive in the stream's order key — internal-id, or `(sort-value, internal-id)` under `_sort` — already merged across the node's shards by the inner k-way merge. Because that merge interleaves shards, each result carries its `shard` explicitly; without it the coordinator could not maintain the per-shard continuation cursors. `id` serves the outer merge's tiebreak and becomes the cursor value (the materialized body carries only the logical id). `num-changes` identifies the matched version for follow-up calls — `/cluster/include` seeks need `(id, num-changes)` — sparing the coordinator a `ResourceAsOf` point read per result for a value the owner already holds from its index scan. `sort-value` is the outer merge key, shipped so the coordinator never re-extracts it from the body. `resource` is the fully materialized resource — storage transforms (field elision, internal-id references) reversed by the owner — in `blaze.fhir.spec` internal CBOR; the coordinator decodes to the internal FHIR model for Bundle assembly, and client-facing serialization happens at the FHIR layer as usual. Reference follow-ups are deliberately absent: `_include` / `_revinclude` are not part of the sub-query (see *Cross-shard `_include` / `_revinclude`*) — owners stream until cancelled and cannot know which results make the page, so per-result ref work here would be speculative.
- **`end`** signals clean exhaustion of all served shards. A connection close without `end` or `error` is truncation: the coordinator re-dispatches this node's shards from its current per-shard cursors. When the coordinator cancels with RST_STREAM (page full), no terminal frame is expected — the owner just stops.
- **`error`** is the in-band terminal for anomalies raised after streaming began (the HTTP status is already committed): the owner emits the anomaly's category and message and closes. The coordinator maps it onto the request's FHIR-layer error handling. Anomalies detectable before the first frame (malformed request, unknown `tid`) are ordinary HTTP error responses instead.

### `/cluster/include` and `/cluster/rev-include` requests and responses

One POST per node owning shards of the page's matches (see [Cross-shard `_include` / `_revinclude`](#cross-shard-_include--_revinclude)) and per direction — the two endpoints mirror `blaze.db.api`'s `include` / `rev-include` one to one; a query using both `_include` and `_revinclude` issues both in parallel. Requests and responses are single CBOR maps — no streaming; payloads are bounded by the page. The split also lets each request carry exactly what its seek needs: forward refs are per *version*, while reverse rows are keyed by the version-less target.

**`/cluster/include` request:**

| Field      | Required | Data Type                                     | Description                                                                                                      |
|------------|----------|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `t`        | yes      | number                                        | the page's pinned snapshot `t`                                                                                   |
| `handles`  | yes      | map shard-id → `[internal-id, num-changes][]` | the page's matched versions, grouped by the shard that emitted them (`shard` / `num-changes` from the result frames); the shard-id is the seek prefix |
| `includes` | yes      | `[{c-hash, target-tid?}]`                     | `_include` specs: the reference search param, optional target-type filter (`_include=Type:param:Target`)         |

Per handle and spec the owner runs one local prefix seek on `ResourceReference` under `(shard-id, internal-id, num-changes, c-hash[, target-tid])`, collecting target internal-ids. The handle's `num-changes` selects the matched version's refs; targets need no validity check — they are identities, resolved to current versions by the coordinator.

**`/cluster/rev-include` request:**

| Field      | Required | Data Type                    | Description                                                                                                      |
|------------|----------|------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `t`        | yes      | number                       | the page's pinned snapshot `t`                                                                                   |
| `handles`  | yes      | map shard-id → internal-id[] | the page's matched ids, grouped by the shard that emitted them; reverse rows are keyed by the version-less target, so no `num-changes` |
| `includes` | yes      | `[{c-hash, tid}]`            | `_revinclude` specs: the reference search param and the source type (`_revinclude=Source:param`)                 |

Per handle and spec the owner runs one local prefix seek on `ReferenceResource` under `(shard-id, internal-id, c-hash, tid)`, collecting source `(internal-id, num-changes)` pairs and keeping only sources current at `t` via the usual `num-changes` equality check against `ResourceAsOf`. That check's `ResourceAsOf` read also yields each source's `shard-id`; both ride back in the response.

**`/cluster/include` response:**

| Field      | Required | Data Type                | Description                                                                                                     |
|------------|----------|--------------------------|------------------------------------------------------------------------------------------------------------------|
| `refs`     | yes      | internal-id[]            | deduplicated set of referenced target ids across the batch                                                      |
| `declined` | no       | map shard-id → node-id[] | shards in `handles` this node no longer read-owns, with their current owners — same [routing validation](#routing-validation) as `/cluster/query`; the coordinator re-issues those shards' handles to a current owner |

**`/cluster/rev-include` response:**

| Field      | Required | Data Type                                  | Description                                                                                                     |
|------------|----------|--------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `refs`     | yes      | `[internal-id, num-changes, shard-id][]`   | deduplicated set of referencing source versions, already resolved and currency-checked at `t`                   |
| `declined` | no       | map shard-id → node-id[]                   | as above                                                                                                        |

Both endpoints return ids, not bodies. The answering node does not in general own the resources it names — forward targets live wherever their own placement put them, and reverse *sources* live on their own subjects' shards even though the reverse *index rows* sit on the match's shard — so returning bodies would force owners into nested cross-shard fetches of their own. Ids also pay off where the owner does hold the body: the coordinator dedups the union across all nodes before any body moves (include targets are heavily skewed toward a few hot reference-data resources), and `:global-replicated` targets — the most common kind — then resolve as zero-transfer local reads on the coordinator itself.

The two `refs` shapes mirror what each direction's seek holds in hand — the same asymmetry as the requests. A forward target appears in `ResourceReference` as a version-less identity (references point at resources, not versions), so its current `num-changes` and shard only exist as a `ResourceAsOf` resolution at `t`. Owner-side that resolution would run per reference occurrence, pre-dedup, repeated across nodes for hot targets; coordinator-side it runs once per unique id (`ResourceAsOf` is global, so the reads are local either way) and doubles as the inclusion filter the coordinator must apply regardless — phantom targets (no `ResourceAsOf` row) and targets deleted at `t` (tombstone) drop out of the Bundle here. So `include` returns bare ids. The reverse currency check, by contrast, has already read each surviving source's `ResourceAsOf` row: `num-changes` and `shard-id` are in hand, and returning them — a few bytes per source on a page-bounded set — spares the coordinator any `ResourceAsOf` reads for `rev-include`; sources arrive resolved, ready for body fetching. Cross-node dedup keys on the internal-id; tuples for the same source from different nodes are identical, since all owners answered at the same pinned `t`.

The multi-node source is not exotic: a multi-valued reference parameter fans one source across every target's shard by construction. `GET /Patient?…&_revinclude=Group:member` with a cohort `Group` referencing member Patients on several shards is found by each member shard's reverse scan and returned by every dispatched node holding a page match — deduplicated to one Bundle entry at the coordinator, whose body read is then local (`Group` is `:global-replicated`). Same pattern: `List:item`, `Appointment:actor`, `CareTeam:participant`. Single-valued reference params (`Observation:subject`) cannot duplicate — one reference, one shard.

Either way each direction yields one deduplicated set; Bundle assembly marks every included resource `search.mode = include` regardless of which spec produced it. Errors are ordinary HTTP error responses — the responses are single CBOR items, so no in-band error framing is needed.

## Apply Pipeline

The v2 apply path (Layer 1 per-resource WriteBatches → Layer 2 tx-index → Layer 3 TxSuccess) carries over with three deltas: per-shard fan-out in Layer 1, shard-aware body fetching, and a cross-shard hook in the rewrite stage's conditional clauses.

### Per-shard fan-out

For a transaction `T` operating on resource `X`'s new version `v_n`, each applying node looks at every command and computes, for each command, which of its owned shards are touched. Three roles per (command, shard):

- **Owner** — the version's primary shard. Writes the full per-resource WriteBatch: body, non-compartment sharded indices, owner-side compartment entries, forward refs, `PatientLastChange`.
- **Compartment member (non-owner)** — a shard whose patient is a member of v_n via a non-primary path. Writes the compartment index entries for that compartment plus a `PatientLastChange` entry for the member patient (so CQL cache invalidation observes the change in this patient's compartment too).
- **Reference target** — a shard owning a target of one of v_n's forward references. Writes only the reverse-ref index entry.

Plus, on every node:

- **Layer 2 tx-index WriteBatch** — `ResourceAsOf` / `TypeAsOf` / `SystemAsOf` row for v_n with its `shard-id`, mapping CF inserts for any newly-allocated ids, `TypeStats` / `SystemStats` deltas.
- **Layer 3** — `TxSuccess` + `TByInstant`.

Each node aggregates per-shard writes from all of `T`'s commands into per-shard `WriteBatch`es and commits them in parallel — same Layer 1 parallel pattern as unsharded v2, just pre-filtered by shard ownership.

### Shard-aware body fetching (distributed variant)

For Kafka-based body shipping (v2 inline/spill via `BlobStore`):

- The Kafka commands message is broadcast — every node receives the bytes.
- Inline-body CBOR decoding is skipped for commands where no owned shard is touched.
- `BlobStore` fetches are issued only for commands where at least one owned shard is touched. A bulk-import transaction with bodies in `BlobStore` ends up with each node fetching `~R/M` of the bodies — natural bandwidth distribution without coordination.

### Conditional clause cross-shard fan-out

The v2 rewrite stage resolves conditional references (`Patient?identifier=X`) and conditional update targets against `db(t-1)`. With sharding, `db(t-1)` is spread across all shards.

For each conditional clause `C` in transaction `T`, every applying node sends a `db(t-1)` search query to one replica of each shard it doesn't own. Determinism is structural: every shard's replicas are identical at `t-1` (the apply loop's strict ordering guarantees this); every node aggregates the same per-shard responses with the same logic; every node reaches the same resolution outcome.

Cost per clause: each node owns `R·N/M` shards and searches them locally; the remaining `N − R·N/M` shards must be searched remotely, and **every one of the M nodes does this independently** (each needs the resolution outcome for its own rewrite, Layer-2 rows, and internal-id minting — there is no leader to compute it once). Cluster-wide that is `N(M − R)` small per-shard searches — for `M=10, R=2, N=256`: ~2000 — batched per peer node into `M × (M − 1)` requests (~90), each carrying the shard list to search on that peer, same dispatch shape as scatter-gather reads. Most transactions have no conditional clauses; for those, no cross-shard traffic occurs. The `M×` multiplier is what the leader-based optimisation in [Open Items](#open-items-deferred) would remove.

Conditional clauses against `:global-replicated` types (e.g. `Practitioner?identifier=…`) resolve locally — the data is on every node. Only conditional clauses against `:patient-scoped` types fan out.

### Internal-id minting

Unchanged from v2. The canonical sort over `(tid, logical-id)` pairs is byte-deterministic. Every node mints the same id for the same logical id. The new bit: each newly-minted version's `shard-id` is computed in the same step as its internal-id allocation — from the resolved subject — and recorded in the Layer 2 `ResourceAsOf` row. Same input, same algorithm, same output on every node.

### Crash safety

v2's three-layer crash story carries over directly. Each node's local `head(TxSuccess)` is its authoritative `t`; rows with `t > head(TxSuccess)` are inert. Re-application from the tx-log is idempotent per-shard (same shard ownership on restart, same row keys, same end state). A rebalance during a node outage is operator-coordinated — when the node restarts and sees the new `ClusterConfig`, it `RangeDelete`s prefixes for shards it no longer owns.

## Read Pipeline

Any node accepts FHIR requests over the public port and becomes the **coordinator** for that request. The coordinator uses globally-replicated metadata to resolve routing and then dispatches sub-queries over the cluster channel to one or more owner replicas. Owners materialize their shard's matched resources using the v2 materialized-resource cache (which lives on owners only — see [Cache Topology](#cache-topology)) and stream finished FHIR back; the coordinator merges and assembles the response Bundle.

### Routing

For a request that names a logical id:

```
internal-id ← LogicalIdToInternalId[(tid, logical-id)]              (local, Caffeine-cached)
asof-row    ← ResourceAsOf seek (internal-id, t-desc = current-t)    (local, block-cache hit)
shard       ← asof-row.shard-id
nodes       ← ClusterConfig.assignment[shard]                        (local, in-memory)
target      ← pick one replica from nodes
```

If `target == self`, dispatch sub-query to self (degenerate, single-node case). Otherwise, dispatch to `target` over the cluster channel.

For a request without a logical id (`/Observation?…`, `/_history`), the coordinator skips the first two steps and goes to the scatter-gather pattern.

### Single-shard reads

Instance reads (`GET /Type/{id}`, `vread`, `_history` for non-re-subjected resources), compartment-scoped reads (`Patient/{X}/$everything`, `Patient/{X}/Observation?…`, single-shard CQL `Patient` context):

```
coordinator                                  owner replica
─────────────────                            ───────────────
resolves shard locally                       receives sub-query on /cluster/query
                                             with shards = [single shard]
                                              
issues /cluster/query with                   runs full FHIR query against local
shards = [resolved-shard], t                 indices, materializes results (uses
                                             local materialized-resource cache)
                                             streams CBOR-encoded FHIR resources
                                             (one CBOR item per resource)
assembles Bundle from received
resources, returns to client
```

The owner does the FHIR work; the coordinator handles Bundle assembly and any cross-shard `_include` follow-ups.

### Scatter-gather (multi-shard) reads

Type search (`GET /Observation?…`), system history, cluster-wide CQL:

```
coordinator                                          per-node sub-query
─────────────────────                                ────────────────────────────────
for each shard s ∈ relevant-shards:                  opens parallel iterators on
    replicas[s] ← ClusterConfig.assignment[s]        each owned shard's CF prefix
    picked[s]   ← pick one read-serving replica (:owned/:outgoing, not :incoming)
                                                     runs the v2 query planner
group picked by node-id, producing                   per-shard, materializes results
by-node = node → [list of shards]                    
                                                     k-way merges per-shard streams
for each node n with shard-list L:                   
    POST /cluster/query with                         streams CBOR resources back
    shards = L, t = pinned-t                         with HTTP/2 flow control
    (parallel)                                       
                                                     pauses send when window full
k-way merges per-node streams,                       (back-pressure)
emits to client until page-size                      
                                                     on RST_STREAM (coordinator
once page reached:                                   page full), closes shard
    RST_STREAM all node streams                      iterators, scan stops
    build continuation token from                    
    per-node + per-shard cursors                     
```

**Dispatch is per-node, not per-shard.** A node owning 32 shards receives one sub-query with all 32 in `shards` and iterates them in parallel internally. Cluster-wide HTTP requests for a full-cluster query: `M` (one per node) instead of `N` (one per shard). For `:patient-scoped` types, `relevant-shards` additionally carries the `0xFFFF` sentinel — assigned to exactly one node — covering versions whose subject didn't resolve (see [Merge, ordering, and dedup](#merge-ordering-and-dedup)).

**Routing is validated per shard, not by config version.** The owner serves only the shards in `L` it still read-owns and reports any it no longer owns (with their current owners), so the coordinator re-dispatches just those — see [Routing validation](#routing-validation). Config changes touching other shards never invalidate an in-flight query.

**Two-level merge.** Per-shard streams merge inside each node first; per-node streams merge at the coordinator. Both stages use the same k-way merge implementation; the only difference is whether streams come from local iterators or HTTP/2 responses.

**Streaming + cancellation** avoid over-fetching. Asking each node for the full page size would have nodes materialize `P` resources each while the coordinator uses `~P/M`; asking each for `P/M` underfills on skewed result sets. Streaming with HTTP/2 back-pressure and RST-STREAM cancellation lets each node scan exactly as far as the coordinator drew.

### Merge, ordering, and dedup

The cross-shard merge is a **merge, not a union** — owners hand the coordinator streams that are already disjoint, so the coordinator never deduplicates across shards.

**The root invariant is schema-level: `ResourceAsOf` records exactly one `shard-id` per version.** The value field carries a single 2-byte `shard-id` — ownership is a total, single-valued function of the version, fixed at apply time and not representable any other way. `ResourceAsOf` is a Global CF, so at any pinned `t` every node resolves, locally, each resource to its current version and that version to its unique owner shard. Disjointness of the per-shard streams derives from this:

- **Owner-anchored rows live on that shard.** Apply writes a version's `Resource` / `SearchParamValueResource` / `ResourceSearchParamValue` / `ResourceReference` rows under the version's `shard-id` — the same value later read back from `ResourceAsOf`.
- **Owners emit resolved current handles, not raw index hits.** Each owner runs the full local query — the `or`-disjunction union (`ordered-index-handles`, *distinct and id-ordered*) and the `num-changes`-equality validity check against `ResourceAsOf` — and streams distinct, current-version handles in the query's order key (internal-id by default, `(sort-value, internal-id)` under `_sort`). The validity check discards stale entries left on former owner shards (e.g. the pre-subject-change version after a `$merge`), so the only shard that can emit a resource is the unique owner of its current version.
- Hence no two shard streams ever carry the same internal-id. The two-level merge differs only here: the **inner** (per-shard) level deduplicates via the `or`-union; the **outer** (cross-shard) level cannot collide and does not.

**Three dispatch invariants** complete the proof on the coordinator side:

1. **One replica per shard.** With `R > 1` the coordinator queries exactly one replica of each shard; replicas are never double-scanned.
2. **`:global-replicated` types are never scattered.** Their data is a full copy on every node, so a type search on such a type is a single-node local query (any node holds the complete result). Scattering it would return the same rows from every node.
3. **The `0xFFFF` prefix is dispatched to exactly one node.** A `:patient-scoped` version whose subject doesn't resolve carries `shard-id = 0xFFFF` and is physically present on every node — the one case where a single-valued shard-id does not mean a single location. Those rows are real results of a type search, so the coordinator adds the sentinel to exactly one dispatched node's `shards` list; routing validation treats `0xFFFF` as read-owned by every node. Because the anchor *value* is still unique, exactly-once dispatch remains trivial.

If any invariant were violated the coordinator *would* have to union and deduplicate — which is precisely why they are invariants, not optimizations.

**Compartment indices are multi-shard by design — and never cross-shard-merged.** `CompartmentResource` / `CompartmentSearchParamValueResource` anchor on the member patient's shard, so a version in K compartments has rows on K shards. This never meets the merge because compartment queries name a single patient and dispatch to that patient's shard alone; their safety is a dispatch-shape fact, not an anchoring fact.

**Sorting across shards.** Under `_sort`, each owner delivers its shard's results in `(sort-value, internal-id)` order and the coordinator k-way-merges on the sort value carried with each streamed result, with internal-id breaking ties. *How* an owner produces that order is its own planner's cost decision per [Core Storage › Sorting](v2-core-storage-redesign.md#sorting): scan-sorted over its `SearchParamValueResource` slice (`shard-id` prefix, then `c-hash, tid, value, …`) or buffered top-K over its filter matches. Per-shard result size and cardinality differ, so the chosen mode can differ shard to shard and page to page; the stream contract hides it from the coordinator, and the scan-to-buffered bail-out runs per shard independently. The per-shard continuation cursor — a bare internal-id — resumes either mode; the sort resume bound is re-derived at the owner (see [Pagination](#pagination)). The *sortable ⇔ value-ordered index* constraint guarantees every shard has the scan-sorted fallback for broad filters, keeping the merge streaming with no whole-result buffering anywhere.

**Reverse-reference paths are the exception and need their own treatment.** `ReferenceResource` anchors on the *target's* shard, so a source version referencing targets on different shards is listed on each of them. For `_revinclude` this is harmless: included resources are deduplicated at Bundle assembly (bounded by page size), exactly as in the unsharded case. For `_has`-driven primary matches it is a genuine duplicate source — the same source can be emitted by every shard owning one of its matching targets — so `_has` cannot ride the dedup-free merge; it needs coordinator-side dedup or a distinct execution plan, to be specified when the chained / `_has` / composite paths are detailed.

### Pagination

The continuation token (`__page-id` from v2) carries:

- The pinned `t` for cross-page snapshot consistency.
- For each dispatched node, the per-shard cursor — the last-emitted internal-id per shard the node iterated (a bare start-id, as in v1).
- The sort field (if any) and a query-hash for safety.

Encoded as CBOR + base64url. Worst-case size for an all-shards query on a 256-shard cluster: ~3 KB; typical queries (touching a handful of shards) are well under 1 KB.

On resume, the coordinator re-issues per-node sub-queries with the same `shards` lists and per-shard cursors. Under `_sort` each owner re-derives its resume bound `(sort-value, internal-id)` from the cursor id with one point read at the pinned `t` — deterministic, since the snapshot is immutable across pages — then seeks its iterators and resumes the k-way merge. A cursor resource purged between pages makes its token unresolvable; the request fails cleanly rather than mis-positioning (the same rare degradation v1 paging has under purge).

### Cross-shard `_include` / `_revinclude`

Include processing keeps its v1 API boundary: `_include` / `_revinclude` are not query clauses — the interaction layer resolves them after the query, against the page's matches, through `blaze.db.api`'s include surface, which is unchanged. Under sharding the coordinator implements that resolution over the cluster channel rather than folding it into the sub-query, for two reasons:

- **Page membership is unknown to owners.** Owners stream until the coordinator cancels, so per-result ref work at the owner would be speculative — paid for results past the page boundary, and for `_revinclude` a wasted reverse-ref scan per over-scanned result. Include resolution inherently operates on the fixed page.
- **Both ref indices of a match are anchored on its own shard.** For a matched resource X, forward refs (`ResourceReference`, sharded by source) and reverse refs (`ReferenceResource`, sharded by target) both live on `shard(X)` — the shard that emitted X. Ref lookups therefore route exactly like the results came in.

The coordinator's include step, once the page is fixed:

1. Group the page's matched handles by the emitting shard's owning node.
2. One `POST /cluster/include` and/or `/cluster/rev-include` per node — issued in parallel when a query uses both — carrying that node's share of the page's handles, the corresponding specs, and the pinned `t` (requests and responses defined under *Cluster Channel*). The owner answers with the deduplicated set of referenced (`include`) or referencing (`rev-include`) internal-ids.
3. Dedup across nodes. `include` target ids are resolved to shard and current `num-changes` locally via `ResourceAsOf`, dropping phantoms and targets deleted at `t`; `rev-include` sources arrive already resolved.
4. Fetch bodies for what isn't already local: `:global-replicated` targets (Practitioner, Organization, Medication — the most common include targets) and own-node residents are local reads; only genuinely remote `:patient-scoped` resources go through `/cluster/body/...`, grouped per node. Subject-path includes are typically same-shard as their source, so most pages need few or no body RPCs.
5. Stitch into the final Bundle with `search.mode = include`.

### Cache Topology

| Cache                       | Where                    | Holds                                            | Invalidation                       |
|-----------------------------|--------------------------|--------------------------------------------------|------------------------------------|
| Materialized resource cache | Owner replicas only      | `(internal-id, num-changes) → materialized FHIR` | None — stable key, immutable value |
| `LogicalIdToInternalId`     | Every node (global)      | `(tid, logical-id) → internal-id`                | None — append-only                 |
| `InternalIdToLogicalId`     | Every node (global)      | `internal-id → (tid, logical-id)`                | None — append-only                 |
| CQL expression cache        | Owner replicas only      | `(expression, shard) → Bloom filter`             | None — staleness handled by guards at use ([Per-shard Bloom-filter cache](#per-shard-bloom-filter-cache)) |
| RocksDB block cache         | Each node (its own data) | Hot SST blocks for sharded + global CFs          | LRU                                |

The materialized-resource cache deliberately lives only at owners: a resource on `shard(X)` is cached on the `R` replicas of `shard(X)`, so every coordinator forwarding to one of those replicas benefits from a centralized cache for that shard. Per-coordinator caching would replicate hot sets across `M` caches with `R/M` effective coverage.

For optional within-shard cache stickiness, the coordinator can use `hash(internal-id) mod R` to pick a primary replica deterministically — see [Open Items](#open-items-deferred).

## Database API under Sharding

The FHIR REST handlers, CQL, and the admin jobs consume the database exclusively through `blaze.db.api` (`modules/db/src/blaze/db/api.clj`). The [Read Pipeline](#read-pipeline) describes the cluster mechanics; this section walks the API surface function by function and answers a sharper question: **can sharding hide entirely behind the existing API, or does the API have to change?**

The answer: the API survives almost unchanged. The [CF placement](#cf-placement) split carries the result — identity (`LogicalIdToInternalId` / `InternalIdToLogicalId`), version metadata (`*AsOf`), transaction metadata (`TxSuccess` / `TxError` / `TByInstant`), and stats are globally replicated, so **every operation on resource handles, history, and totals is a local read on any coordinator**. Sharding surfaces only where search execution, body access, per-version index rows, or index writes are involved. The concrete deltas: two API additions (batched `include` / `rev-include`), one redefinition (`explain-query`), one operational redesign (`re-index`), and three functions constrained to owner-local use (`patient-compartment-last-change-t`, matcher evaluation, per-handle `include` / `rev-include`).

### Function-by-function

**Node-level.**

| Function | Sharding effect |
|---|---|
| `db` | Returns a value at the coordinator's local `head(TxSuccess)` — node state, local, unchanged. The value's `t` governs all sub-queries; lagging owners sync up to it ([Read Consistency](#read-consistency)). |
| `sync` (0-arity) | Asks the tx log for the last **submitted** `t` (the tx log is global) and waits locally. Unchanged. (The api.clj docstring says "complete" where the semantics are "submitted" — worth fixing independently of sharding.) |
| `sync` (with `t`) | Local wait on `head(TxSuccess)`. Unchanged. The same wait runs owner-side per sub-query. |
| `transact` | Submission to the global tx log; the result wait runs on local `TxSuccess` / `TxError` (Global CFs, applied by every node). Unchanged. |
| `changed-resources-publisher` | Publishes resource *handles* derived from `TypeAsOf` at each new `t` — a Global CF, fully local. Consumers pulling bodies pay the normal `pull` routing; the job machinery's Task resources carry `shard-id = 0xFFFF` (no resolvable subject) and are local everywhere. |
| `node`, `as-of`, `since`, `since-t`, `t`, `basis-t`, `as-of-t`, `tx` | Pure accessors or point reads on `TByInstant` / `TxSuccess` — Global CFs, local. |

**Instance-level.**

| Function | Sharding effect |
|---|---|
| `resource-handle` | `LogicalIdToInternalId` + `ResourceAsOf` point reads — Global CFs, zero RPC on any node. The returned handle carries the version's `shard-id` from the `ResourceAsOf` value; that field is what routes every later body access. |
| `resource-handle?`, `deleted?` | Pure. |

**Type- and system-level.**

| Function | Sharding effect |
|---|---|
| `type-list`, `system-list` | Current-resource enumeration over Global CFs (`LogicalIdToInternalId` scan + `ResourceAsOf` currency check — the same pattern as the `_id` sort in [Core Storage › Sorting](v2-core-storage-redesign.md#sorting)). Fully local; only body pulls fan out. |
| `type-total`, `system-total` | `TypeStats` / `SystemStats` — Global, O(1), local. |
| `type-query`, `system-query`, `compile-type-query`, `compile-system-query`, `-lenient` variants | Compilation is local (the search-param registry is code). Execution is the scatter-gather path over `/cluster/query`; the returned reducible is fed by the two-level merge. The single `start-id` argument seeds a multi-shard resume correctly: every shard's stream is emitted in the query's order key, so each owner seeks to `≥ start-id` and the merge resumes. The per-shard cursors in the continuation token are an optimization of exactly this seek, not an API requirement. |
| `compile-type-matcher`, `compile-system-matcher` | Compilation local; evaluation is owner-local — see [Matchers](#matchers). |

**Compartment-level.**

| Function | Sharding effect |
|---|---|
| `compartment-type-list`, `compartment-query`, `compile-compartment-query`, `-lenient` | Single-shard dispatch: `shard(patient-logical-id)` is computable locally before any DB read. The whole query runs on one owner. |
| `patient-compartment-last-change-t` | `PatientLastChange` is **Sharded** by patient; for a non-owned patient this would be a remote point read behind a synchronous signature. Its only consumer (`blaze.elm.resource` — the CQL Bloom-filter staleness guard) runs owner-local under the CQL fan-out, so the function is **constrained to owner-local use**. A future coordinator-side caller would force an async variant. |

**Common query functions.**

| Function | Sharding effect |
|---|---|
| `count-query` | Per-shard streams are disjoint (single `shard-id` per version — see [Merge, ordering, and dedup](#merge-ordering-and-dedup)), so per-shard counts sum. Needs a count mode on `/cluster/query` (owners count instead of materializing — a protocol addition, not an API change). Already returns a future. |
| `execute-query` | See `type-query`; a pre-compiled query ships as the parsed clause format the channel already carries. |
| `explain-query` | **Redefined.** A single plan no longer exists: each owner plans its shards independently and may pick a different execution mode per shard and per page. Returns the coordinator's dispatch plan (touched shards, picked replicas, sentinel handling), optionally with per-shard plans collected from owners. |
| `matches?`, `matcher-transducer` | Matchers post-filter handles by reading their `ResourceSearchParamValue` rows — a **Sharded** CF, owner-anchored. See [Matchers](#matchers). |
| `query-clauses`, `matcher-clauses` | Pure. |

**History.**

| Function | Sharding effect |
|---|---|
| `instance-history`, `type-history`, `system-history`, `changes` | Driven entirely by `ResourceAsOf` / `TypeAsOf` / `SystemAsOf` — Global CFs. **The whole `_history` surface is sharding-oblivious at the handle level: fully local, zero RPC.** Each entry carries its per-version `shard-id`, so the subsequent `pull-many` routes every version to its owner — including resources whose versions live on different shards after a subject change. |
| `total-num-of-instance-changes`, `total-num-of-type-changes`, `total-num-of-system-changes` | Global CFs / stats. Local. |

**Include and everything.**

| Function | Sharding effect |
|---|---|
| `include`, `rev-include` | Both ref indices of a handle anchor on the handle's own shard, so for a non-owned handle the prefix seek is remote, and the per-handle signature would put one RPC inside every page-iteration step — see [Batched include / rev-include](#batched-include--rev-include). |
| `patient-everything` | Compartment-scoped: routed wholesale to the patient's owner. Supporting resources are `:global-replicated` (local at the owner); secondary-membership bodies are the owner's cross-shard fetches. |

**Batch DB and pull.**

| Function | Sharding effect |
|---|---|
| `new-batch-db` | Snapshot/iterator reuse is a node-local optimization; remote sub-queries open per-call iterators at the pinned `t` on owners. The contract (AutoCloseable, stable view at `t`) holds regardless: versions `≤ t` are immutable. |
| `pull`, `pull-content` | The handle's `shard-id` routes: local read, `/cluster/body` RPC, or local for `0xFFFF`. Already async — the signature fits sharding as-is. |
| `pull-many` | Handles group by owner node for batched body fetches. Handles that came out of a query stream already carry their materialized resource ([prefetched bodies](#handles-carry-prefetched-bodies)) — then this is zero-RPC. `:variant` / `:elements` projection applies at the owner or coordinator. |

**Re-index.**

| Function | Sharding effect |
|---|---|
| `re-index-total` | Counting over Global CFs. Local. |
| `re-index` | **Not transparent** — see [Re-index under sharding](#re-index-under-sharding). |

### Handles carry prefetched bodies

Owners stream fully materialized resources ([`/cluster/query` response](#clusterquery-response)), but the API contract is handles-then-`pull-many`. The reconciliation: a handle that arrives via a sub-query stream carries its already-materialized resource, and `pull` / `pull-many` on such a handle is a map lookup. Without this, every search would degrade into a second round of per-resource body RPCs. Handles minted locally (`resource-handle`, history, `type-list`) carry no body and pull through the normal shard routing.

### Reducible collections over the network

`execute-query` results, the history collections, `include` / `rev-include`, and `patient-everything` return synchronous reducibles. Under sharding their reduction blocks on HTTP/2 streams and can fail mid-reduce with network anomalies — v1 reducibles can already throw from RocksDB read errors, but the failure modes become far more common. Two contract notes: the declined-shard re-dispatch of [routing validation](#routing-validation) happens invisibly inside the reduction; and a network failure that survives re-dispatch surfaces as an anomaly from the reduce, exactly as a storage error does today.

### Matchers

Matchers evaluate by reading the handle's `ResourceSearchParamValue` rows, which live on the handle's owner shard. Three call-site families, three answers:

- **CQL retrieves** (`blaze.elm.compiler.queries`) — run owner-local under the [CQL fan-out](#cql-fan-out), where every evaluated handle's rows are local. No change.
- **The read-only-resource check in `tx_indexer.verify`** — runs at apply on every node against `db(t-1)` handles whose rows may be on non-owned shards. It is a deterministic search-clause check against `db(t-1)` — exactly the shape of conditional-clause resolution — and rides the same [cross-shard fan-out](#conditional-clause-cross-shard-fan-out).
- **A future coordinator-side caller** would need either owner-side evaluation (ship handle + clauses to the owner; the channel already carries both shapes) or body-based matching (evaluate clauses against the materialized body in hand). Nothing in scope needs this today; the choice is an open design question (see [Open Items](#open-items-deferred)).

### Batched include / rev-include

`d/include` and `d/rev-include` are per-handle, and the interaction layer's include step iterates a page of handles. Per-handle RPCs would put one network round-trip inside every iteration; the cluster design batches per node instead (`/cluster/include`, `/cluster/rev-include` — see [Cross-shard `_include` / `_revinclude`](#cross-shard-_include--_revinclude)). The API therefore grows two batched entry points:

- `include-many` — a page of handles plus include specs → the deduplicated set of target handles.
- `rev-include-many` — a page of handles plus rev-include specs → the deduplicated, currency-checked set of source handles.

`modules/interaction/src/blaze/interaction/search/include.clj` ports to the batched functions. The per-handle `include` / `rev-include` remain — correct on any node, efficient on the handle's owner, and the degenerate single-handle case of the batch. Owner-local traversals (CQL, `patient-everything`) keep using them directly.

### Re-index under sharding

`d/re-index` writes search-param index rows directly through the resource indexer — into Sharded CFs. Only a shard's owners can rewrite its rows, and all `R` replicas must, so re-index becomes a **per-owner operation over owned shards**, and the single `(start-type, start-id)` cursor stops describing cluster progress:

- The `job-re-index` job becomes a coordinator: it fans the work out per node over the cluster channel; each node re-indexes the resources of its owned shards with a local cursor.
- Progress is tracked **per shard**, not per node, so a shard move during a re-index job doesn't lose the shard's progress: the new owner resumes from the job's recorded per-shard cursor. The moved data includes whatever rows the old owner already rewrote, and re-indexing is idempotent per row, so overlap is harmless.
- `re-index-total` stays a local count.

### Summary

| Category | Functions |
|---|---|
| Fully transparent, fully local | `db`, `sync`, `transact`, `changed-resources-publisher`, all accessors, `resource-handle`, `type-list` / `system-list`, all totals and stats, the entire history surface, all compile functions |
| Transparent, with cluster work behind the API | `type-query` / `system-query` / `execute-query` / `count-query` (scatter-gather), compartment queries (single-shard dispatch), `pull` / `pull-content` / `pull-many` (shard-routed), `patient-everything`, `new-batch-db` |
| Redefined semantics | `explain-query` (dispatch plan + per-shard plans) |
| API additions | `include-many`, `rev-include-many` |
| Constrained to owner-local use | `patient-compartment-last-change-t`, `matches?` / `matcher-transducer`, per-handle `include` / `rev-include` (efficiency) |
| Operational redesign | `re-index` (per-owner execution, per-shard progress) |

## CQL Fan-Out

CQL `Patient`-context evaluation is the highest-value path for sharding. Blaze's v2 unsharded pipeline already evaluates per-patient in parallel within one JVM (see [CQL pipeline](../implementation/cql.md)); sharding adds a layer of cluster-wide parallelism on top.

Population-level evaluation always runs over the complete patient population — there is no evaluation over an enumerated subset of patients (v1 semantics carry over). That makes the dispatch patient-free: the coordinator never enumerates or ships patient ids; a sub-query names shards, and each owner derives its shards' patients locally (see [Owner-local patient enumeration](#owner-local-patient-enumeration)). Single-subject evaluation (`$evaluate-measure?subject=…`, `reportType=subject`) is not a fan-out at all — it routes to the subject patient's shard like any other compartment-scoped read (see [Single-shard reads](#single-shard-reads)).

```
1. Coordinator pins t (Read Consistency), resolves the Measure and its Library
   at t, and translates + compiles the CQL library (unchanged v2 pipeline) —
   validation only, so bad measures and libraries fail once, pre-dispatch, as a
   proper OperationOutcome.
2. Coordinator picks one read-serving replica per shard and groups shards by
   node — the same dispatch shape and per-shard routing validation as
   scatter-gather reads.
3. Dispatch one sub-query per node carrying:
   - the Measure (internal-id)
   - the evaluation parameters (measurement period, report type)
   - the node's shard list
   - the pinned t
   over POST /cluster/cql.
4. Each node resolves the Measure and Library locally at t (both are
   :global-replicated), compiles the library against its local database node,
   enumerates the patients of its assigned shards locally, and runs the
   unchanged measure-evaluation path over that patient set using v2's existing
   Parallel Patient Context Evaluation. Every patient is local; every retrieve
   hits local indices; every same-compartment reference target is local. No RPC
   during evaluation.
5. Each node returns a partial MeasureReport covering its patient set.
6. Coordinator merges the partial MeasureReports component-wise and computes
   derived values (see Merging partial MeasureReports).
```

For a deployment with `M=10` nodes and `N=256` shards evaluating a library across 10M patients, the work fans out to 10 nodes × ~25 shards/node = ~250 concurrent evaluators, each on ~40k patients. Wall-clock time is bounded by the slowest shard, not by the total patient count.

### Owner-local patient enumeration

Each owner derives the patient set of an assigned shard locally: scan `LogicalIdToInternalId` under the `Patient` tid prefix (a Global CF — local read, logical-id-ordered), compute `shard(logical-id)` per entry, keep entries whose shard is in the sub-query's `shards` list, and drop phantoms and patients deleted at the pinned `t` via the same current-version validity check against `ResourceAsOf` that every read path applies — the `_id` sort uses this identical pattern (see [Core Storage › Sorting](v2-core-storage-redesign.md#sorting)). The filter is exact because `:patient-root` placement is a pure function of the logical id: every version of a Patient lives on `shard(logical-id)`, and a Patient never carries the `0xFFFF` sentinel. The scan touches every patient id cluster-wide on each dispatched node, but it is a sequential local read over a dense, well-cached CF plus a per-entry hash — negligible next to expression evaluation.

### `/cluster/cql` request and response

The sub-query names the Measure rather than carrying expression-level instructions. Measure evaluation is more than a list of expressions — population criteria, stratifiers (including multi-component ones), scoring, and report structure all live in the Measure — and `Measure` and `Library` are `:global-replicated`, so every owner resolves exactly the same bytes with a local read at the pinned `t`. Each owner runs the complete, unchanged measure-evaluation path over its patient set; the wire stays at the FHIR level: Measure identity in, MeasureReport out. Translation and compilation run per owner — deterministic, since every node compiles the same library bytes with the same build.

Request — a single CBOR map:

| Field         | Required | Data Type                          | Description                                                                          |
|---------------|----------|------------------------------------|--------------------------------------------------------------------------------------|
| `measure`     | yes      | internal-id (8)                    | the Measure to evaluate, resolved locally to its version current at `t`              |
| `period`      | yes      | `[date, date]`                     | the measurement period (`periodStart` / `periodEnd`)                                 |
| `report-type` | yes      | `"population"` \| `"subject-list"` | determines whether populations carry counts or subject internal-id lists             |
| `shards`      | yes      | shard-id[]                         | the shards this node must evaluate                                                   |
| `t`           | yes      | number                             | pinned snapshot `t` ([Read Consistency](#read-consistency))                          |

Response — a single CBOR map (no streaming: a partial report is small, and subject lists ride as packed 8-byte internal ids bounded by the node's patient count):

| Field      | Required | Data Type                     | Description                                                                                                       |
|------------|----------|-------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `report`   | yes      | MeasureReport (internal CBOR) | the partial report over this node's patient set; under `subject-list`, each population carries its subjects as an internal-id array in place of a `List` reference |
| `declined` | no       | map shard-id → node-id[]      | shards not read-owned here, with their current owners — same [routing validation](#routing-validation) as `/cluster/query`; the coordinator re-dispatches those shards to a current owner |

Errors are ordinary HTTP error responses — the response is a single CBOR item, so no in-band error framing is needed.

### Merging partial MeasureReports

Partial reports are structurally congruent — every node evaluated the same Measure at the same `t`, so groups, populations, and stratifiers align by position and code. The merge is component-wise:

- **Population counts** sum.
- **Strata** merge by stratum value, counts summing; a stratum absent in one partial report contributes zero.
- **Subject lists** (`reportType=subject-list`) concatenate — they are disjoint by shard. The coordinator resolves the internal ids to logical ids, creates the final `List` resources in a regular transaction, and references them from the merged report.
- **Derived values are computed, never merged.** A derived value such as a proportion `measureScore` is not additive; the coordinator computes it from the merged population counts.

### Per-shard Bloom-filter cache

The v2 CQL pipeline keeps Blaze's expression cache: one Bloom filter per cacheable `exists` expression, holding the patients for which the expression evaluated to true, consulted to skip whole-expression evaluations per patient. The cache is keyed by the expression form alone — the database state a filter was built on is carried *in* the filter as its creation `t`, not in the cache key — and two guards make using a possibly-stale filter sound:

- **Per-patient staleness.** A filter's negative answer is bypassed for any patient whose compartment changed after the filter was built (`PatientLastChange` check at evaluation time).
- **No filters from the future.** A filter built on a database state newer than the evaluation's database can't be used to draw conclusions about that older state. Such filters are not attached in the first place — the attach pass sees the cache through a view bounded by the evaluation `t` — with the same check repeated at evaluation time as the semantic backstop.

Under sharding the cache splits by shard and both guards stay owner-local:

- **Keying and placement.** One filter per `(expression, shard)`, on the shard's owner replicas, built over the shard's patient subset via the same owner-local enumeration the sub-query evaluation uses. Tying filters to shards rather than to a node's owned-shard set keeps ownership changes clean: a shard move invalidates exactly that shard's filter, nothing else.
- **Local guards.** `PatientLastChange` is sharded by patient, so the per-patient staleness check is a local index read on the owner. The future-filter bound is the sub-query's pinned `t`.
- **Interaction with pinned `t`.** Sub-queries evaluate at the coordinator's pinned `t`, which can trail this owner's local head. A filter created or refreshed at the owner's head is therefore briefly newer than an incoming pinned-`t` evaluation and is simply not attached until the query's `t` catches up — a short warm-up window, not an error. The common case is unaffected: filters live for many hours between refreshes, so a filter's `t` is normally far below any query's `t`.
- **Persistence and shard moves.** As in unsharded v2, filters are derived, node-local data (an in-memory cache over a node-local persistent store, now keyed with a `shard-id` prefix). They are not part of state-sync: after a shard move the new owner builds the shard's filters lazily on first use, and the old owner drops the moved shard's entries with the same `RangeDelete` prefix cleanup as the sharded data CFs.

Cache memory and filter-build work total roughly the same as unsharded — each shard's filter covers `1/N` of the patients — but are distributed across nodes, matching the per-shard query traffic each owner sees.

### Cross-patient CQL

A CQL library that does cross-patient correlation (e.g. care-team-wide logic) cannot be evaluated independently per shard. For these, the coordinator falls back to pulling the relevant cohort into one node and evaluating centrally — the same path v2 single-node uses today. The shard-parallel path is for the common case of per-patient logic.

## Read Consistency

Each node has its own `head(TxSuccess)` — its position in the single global transaction order. Database acquisition is unchanged from unsharded v2 and stays entirely local to the coordinator:

- `d/db` returns a value at the coordinator's local head. No communication, no blocking. This is the default read acquisition (below).
- `d/sync` with `t` waits for the local head to reach a supplied `t` (session tokens, continuation tokens, vread).
- `d/sync` (0-arity) asks the tx log for the `t` of the last **submitted** transaction and waits for the coordinator's local head to reach it. The semantics are *submitted*, not *applied* — the tx log is the one serialization point, and a submitted transaction is already ordered. This is the strong-mode acquisition (below): a read acquired this way sees every write submitted anywhere before the call, with no client cooperation.

**The db value's `t` is chosen once, locally, by the coordinator.** Sub-queries carry that exact `t`; there is no cross-node `t` negotiation. An owner whose head still trails a sub-query's `t` syncs up before serving — the cluster-server handler waits for its local head to reach `t` before opening iterators, the same wait `d/sync` with `t` performs. The wait is bounded by the owner's apply lag on the shared log and cannot deadlock: the transaction at `t` is already in the log, and the owner will reach it. Reads at the pinned `t` need no snapshot coordination because versions at `t' ≤ t` are immutable; the single-partition tx-log guarantees that all shards' data at one `t` forms a coherent global snapshot.

**Replica selection is head-aware.** For each touched shard the coordinator prefers a read-serving replica whose head is already known to be `≥ t`; head observations piggyback on every cluster-channel response, so the coordinator's view of peer heads refreshes continuously at no extra cost. When no replica of a shard is known to have reached `t`, the coordinator dispatches to the freshest one and the owner-side sync covers the residual gap. Backstop: if every replica of a touched shard lags `t` beyond a configurable freshness threshold, the coordinator waits briefly or returns `412 Precondition Failed` (operator policy).

### Default read acquisition: the local head

FHIR reads acquire the database with `d/db` — a value at the coordinator's last committed `t`. Reads never contact the tx log: **reads do not coordinate with writes.** Read latency and read availability are properties of the serving node (plus its owner sub-queries), the write path is touched by writers only, and a tx-log outage leaves reads running. This is Datomic's read model \[[Datomic](#references)\]: ambient reads at the peer's basis-`t`, with freshness carried explicitly where causality requires it.

The local head already guarantees a lot:

- **Consistent-prefix snapshots.** A db value at the local head is a correct historical state of the entire database — never torn — because there is one global `t` order and versions `≤ t` are immutable.
- **Per-node monotonic reads.** A node's head only advances; a client sticky to one coordinator gets monotonic reads for free.
- **Same-node read-your-writes.** `d/transact` completes only after the local node has committed the transaction, so a client that writes and reads through the same node always sees its own write. With load-balancer stickiness this covers most clients with no further mechanism.

What it does not guarantee is **cross-node** read-your-writes and monotonicity: a write through node A followed by a read through node B can miss the write while B's head trails A's. Session tokens close that gap.

### Session tokens (cross-node read-your-writes)

The client carries the `t` of its last write, and every read syncs the serving node to at least that `t` — `d/sync` with `t`, a bounded local wait, still no tx-log contact. This is Datomic's basis-`t` model \[[Datomic](#references)\], and the guarantee class is the *session guarantees* of Terry et al. — read-your-writes plus, with a max-seen token, monotonic reads \[[Terry 1994](#references)\]. The freshness information travels with the client instead of being fetched from a central point, so reads stay coordination-free. Both transport directions have natural homes in the existing HTTP surface:

- **Server → client: standard FHIR already carries the token.** Blaze's `versionId` *is* the transaction's `t`. Every successful write returns it in the `ETag` header (`W/"<t>"`), the `Location` header (`.../_history/<t>`), and `meta.versionId`; in a transaction Bundle, each entry's `response.etag` carries it. Read responses carry the served `t` as a response header (and inside the continuation token), so a client that tracks the maximum `t` it has seen extends read-your-writes to monotonic reads across coordinators.
- **Client → server: a `Prefer` extension preference** \[[RFC 7240](#references)\].

  ```
  Prefer: blaze-min-t=<t>
  ```

  FHIR already uses `Prefer` for optional, server-may-ignore behaviors (`return=`, `handling=`, `respond-async`), and that is the right shape here: the preference only *raises* the read's floor above the coordinator's head, so a server that ignores it degrades to the default — detectable by the client through the absent `Preference-Applied`, never a silent contract violation by a server that advertises the preference. A coordinator that honors it answers with `Preference-Applied` and waits until its head reaches the supplied `t`.

A client-supplied `t` is untrusted input: a bogus value from the future would otherwise park the request. The same freshness backstop as for lagging owners applies — bounded wait, then `412 Precondition Failed`.

Blaze-controlled clients automate the echo: the frontend and `blazectl` carry `blaze-min-t` on every request within a session. Token-less clients that need cross-node read-after-write have two options: load-balancer stickiness (same-node read-your-writes, above) or the strong deployment mode (below).

**vread is the degenerate case.** `GET /Type/{id}/_history/{vid}` names the needed `t` in the URL itself: the handler syncs to `max(blaze-min-t, vid)` before resolving rather than serving a not-found from an older snapshot, so following a write's `Location` header works on any coordinator with no token at all.

### Strong mode

Deployments with token-less, non-sticky clients that write through one node and immediately read through another — ETL pipelines, conformance test suites — can set the read default to **strong**: every read acquires the database with 0-arity `d/sync`, anchored on the tx log as the single point of freshness. A read on any node then sees all previously submitted writes, with no client cooperation. The costs are exactly what the default avoids: a tx-log round trip per read, and read availability coupled to tx-log reachability. Deployment-level switch: `READ_CONSISTENCY` = `local` (default) | `strong`, documented in `docs/deployment/environment-variables.md`.

### Staleness watchdog

Local-head reads convert a stalled node from *blocking* (loud, immediate) into *serving increasingly stale data* (silent) — unacceptable past some bound in a clinical setting. The mitigation keeps coordination off the read path: a background watchdog periodically compares the local head against the tx-log head and takes the node out of read service (`503`, failing the load balancer's health check) once it has trailed the log head for longer than an operator bound (`MAX_READ_STALENESS`, an ISO-8601 duration, e.g. `PT10S`); a node that cannot reach the log to measure counts as trailing. The per-read coordination of strong mode is amortized into a heartbeat. An idle cluster is never flagged — no submissions means local head and log head coincide.

### Availability

Requiring owners to reach the coordinator's `t` couples multi-shard read latency to apply progress. Under the default acquisition the coordinator itself never waits — its own head defines `t` — so the coupling is only the owners' residual lag behind that head; strong mode adds the coordinator's own catch-up to the log head on top. The gating set:

- **Single-shard reads** are gated by one owner: structurally identical to v1, just possibly a different node than the coordinator.
- **Scatter-gather reads** are gated by `max(apply lag)` over all touched owners — for a full type search, effectively the slowest picked node in the cluster. The p99 latency of multi-shard reads therefore tracks the cluster's worst apply jitter, not the median (per-shard head-lag metrics: see [Observability](#operations--observability)).

Why this is acceptable in practice:

- **Steady-state lag is small, and sharding shrinks it.** Every node tails the same log continuously; the wait is "catch up to what was submitted a moment ago." Per-node apply work is *smaller* than in a v1 distributed deployment: Layer 1 (bodies, search-param indexing) runs only for owned shards (~`R/M` of the cluster's heavy work), and Layer 2/3 is cheap metadata.
- **`R ≥ 2` plus head-aware replica selection absorbs node-local degradation.** A node with a failing disk or a GC storm stops being picked — the same role a load balancer's health check plays for v1 replicas. A read *must* wait only when **all `R` replicas of some touched shard** trail `t`.
- **Correlated stalls are not a sharding regression.** A poison transaction or a giant bulk import stalls deterministic apply on every node in v1-distributed too. Under the default acquisition a cluster-wide stall doesn't block reads at all — nodes keep serving at the stalled head until the [staleness watchdog](#staleness-watchdog) trips; under strong mode it blocks reads exactly as in v1. Bounded wait + `412` remains the backstop wherever a wait exists.

The genuinely new trade-off is **`R = 1`**: one degraded node gates every multi-shard read cluster-wide, and there is no replica to route around it — in v1 the same node would only hurt requests routed to it. `R = 1` buys capacity at the price of coupling cluster-wide read availability to every node's health; deployments that care about read availability run `R ≥ 2`.

### CAP / PACELC classification

Writes and reads classify differently, and stating them separately \[[Brewer 2000](#references); [Gilbert 2002](#references); [Abadi 2012](#references)\] is more honest than one cluster-wide label:

- **Writes are PC/EC.** One serialization point, no fallback: under a partition from the tx log, writes are unavailable rather than inconsistent; in normal operation they pay the log round trip and the serial apply. Identical to v1's distributed variant.
- **Default reads are PA/EL.** Local-head acquisition keeps reads available under any partition and free of coordination latency, serving consistent-prefix snapshots — immutability means a stale db value is a correct historical state, never a torn one: the failure mode is "old," never "wrong." Session tokens layer per-client read-your-writes and monotonicity on top without changing the classification — the token adds a bounded local wait, not coordination.
- **Strong-mode reads are PC/EC.** Anchored on the tx log; a read that cannot learn the last submitted `t` fails rather than answering stale.

Per partition scenario:

- **Node partitioned from the tx log.** Writes through it are unavailable (cluster-wide if the partition isolates the log itself). Strong-mode reads on it are unavailable. Default reads keep serving at the local head until the [staleness watchdog](#staleness-watchdog) takes the node out of service.
- **Node partitioned from peers, tx log reachable.** Coordinators route reads around it via other replicas. The node itself keeps applying — until the next transaction carrying a conditional clause on a `:patient-scoped` type, where the rewrite stage's [cross-shard search fan-out](#conditional-clause-cross-shard-fan-out) cannot complete: its head stops advancing and the staleness watchdog eventually takes it out of read service. In v1, a node partitioned only from its peers keeps applying indefinitely; this apply-time connectivity dependence is new with sharding (see [Failure Modes](#failure-modes)).
- **The remaining dial position.** Owners answering scatter-gather sub-queries still sync to the coordinator's pinned `t`, so cross-shard snapshots stay coherent even in the default mode. The per-request `Prefer: handling=eventual-consistency` open item would drop that too — each owner answering at its own head, accepting torn cross-shard snapshots for latency. The full dial runs strong → session token → default → eventual-consistency, trading freshness guarantees for latency and availability at each step.

### Routing validation

Routing is validated **per shard at the owner**, not against a global config version. Each sub-query names the shards the coordinator assigned to that node (`shards = L`); the receiving node checks each `s ∈ L` against its own live `ClusterConfig.assignment[s]` and serves only the shards it currently **read-owns** (sub-state `:owned` or `:outgoing` — never `:incoming`).

- If every `s ∈ L` is read-owned, the node serves all of them.
- If some `s ∈ L` is not read-owned — it moved away, or the node is still `:incoming` — the node declines those shards and returns its current `assignment[s]` for each (the owner it now sees) in the response's `header` frame, while still streaming results for the shards it does own.
- The coordinator re-dispatches **only the declined shards** to their current owners (using the returned hint, or a refreshed local `ClusterConfig` read) and merges the late results in. Sub-queries that validated are untouched — no full restart.

This fails a sub-query for a shard only when that shard is genuinely missing from the target, so unrelated config churn — an unrelated shard move, a node-registry heartbeat, an RF change, all of which bump the global version — never disturbs a query whose shards are stable. It also closes both drift directions with one local check: a coordinator routing a moved-away shard to its old owner is declined once that owner stops read-owning it (while the transitional `:outgoing` window, which still serves reads, is correctly accepted), and a shard routed to a not-yet-ready `:incoming` node is declined rather than silently under-scanned.

`ClusterConfig.version` is retained only as an **advisory refresh hint**: a node may echo its current version so a lagging coordinator refreshes proactively. It is never a reject gate — correctness rests on per-shard ownership. Cross-page stability composes: a per-shard cursor is a shard position independent of which node serves it, so a shard that moves between pages resumes from the same cursor on its new owner.

## Cluster Operations

### `ClusterConfig`

A globally-replicated CF holding:

```
cluster-id        opaque identifier
N                 shard count (immutable after creation)
R                 target replication factor
assignment        shard-id (0..N-1) → ordered set of node-ids (size = current replicas of that shard)
                                       plus per-node sub-state for that shard
node-registry     node-id → { cluster-address, role, last-seen }
version           monotonically increasing change counter
```

Updates commit via the same tx-log mechanism as data (a "config-change" transaction). The change is visible cluster-wide once its `TxSuccess` lands.

`assignment[s]` entries carry per-node sub-state:

| Sub-state   | Meaning                                                  |
|-------------|----------------------------------------------------------|
| `:owned`    | Fully owned; serves reads and writes                     |
| `:incoming` | Being received from another node; reads not yet served   |
| `:outgoing` | Owned but being handed off; reads served, writes drained |

### Node modes

Extends v2's `:live`, `:migration`, `:joining` with `:draining`:

| Mode         | Use case                             | Reads                 | Writes                |
|--------------|--------------------------------------|-----------------------|-----------------------|
| `:live`      | Normal operation                     | yes (on owned shards) | yes                   |
| `:migration` | v1 → v2 cluster-wide migration       | yes                   | no                    |
| `:joining`   | Initial state-sync for a new v2 node | no                    | no                    |
| `:draining`  | Releasing shards before removal      | yes (on owned shards) | no (for owned shards) |

### Shard moves via state-sync per shard

The v2 state-sync protocol gains a `shard` query parameter. The same wire format (header + per-transaction frames + end frame) carries one shard's worth of data instead of the whole system. The source's `SystemAsOf` iterator is opened with a `shard-id == s` value filter; frames carry only the *sharded* data (bodies, search-param indices, compartment indices, reference indices, `PatientLastChange`). Globally-replicated rows (`ResourceAsOf` etc.) are already on the target.

A move of shard `S` from node `A` to node `B`:

1. **Propose.** Admin endpoint commits a config-change transaction setting `assignment[S]` to `{A: :outgoing, B: :incoming, source: A}`. Once `TxSuccess` lands, both nodes see the new state.
2. **Catch up.** `B` opens `/cluster/state-sync?shard=S&start-t=<B's current t for S>` against `A`. `A` streams everything for shard `S` up to its current `t`. `B` applies frames using the v2 importer, restricted to shard `S`.
3. **Incremental.** New transactions touching shard `S` are still applied on `A` while the stream is open. `B` re-issues the stream from where it left off (state-sync's existing resumability).
4. **Handoff.** When `B`'s lag is below threshold, a config-change transaction sets `assignment[S]` to `{B: :owned}` and removes `A`. After `TxSuccess`, `A` no longer serves shard `S`; `B` does. Atomic at `T.t`.
5. **Cleanup.** `A` issues `RangeDelete` on `shard-id = S`'s prefix in every sharded CF as a background task.

The shard-id prefix on every sharded CF is what makes step 2 a contiguous range scan and step 5 a single `RangeDelete`.

For `R > 1`, a "move" decomposes into "add new replica" + "remove old replica," each a single move of the form above. The shard always has at least `R` replicas during the transition.

### Adding a node

1. Operator starts node `N+1` with cluster CA. Node enters `:joining`.
2. `N+1` reads `ClusterConfig` from any existing node.
3. Operator issues shard-moves giving `N+1` ownership of shards.
4. As each shard arrives, `N+1` marks it `:owned`.
5. Once all assigned shards are owned, operator transitions `N+1` to `:live`.

### Removing a node

1. Operator calls `/__admin/mode/draining` on the node.
2. New writes for its owned shards are rejected; coordinators re-pick another replica.
3. For each owned shard, operator triggers a move to another node.
4. Once all shards are released, the node can be shut down.

### Concurrent moves

Independent moves (different shards) run in parallel; each commits a small config-change transaction. Two concurrent moves of the *same* shard conflict at the config-change apply: the second one's prior-version field no longer matches `ClusterConfig.version` and fails with `409 conflict`. Operator retries.

### Standalone

`M=1, N=1, R=1` is the default standalone topology — degenerate single-shard with the same code path as a distributed cluster. Shard-id prefix is always `0x0000`; partition→node table has one entry; multi-shard query paths are exercised but trivially.

Operators expecting growth deploy standalone with `N=256, R=1` and the same code; later adding nodes runs shard-moves to redistribute. Operators who know they will stay standalone keep `N=1` for zero overhead.

The standalone variant uses v2's in-memory tx-log unchanged; no Kafka, no `BlobStore`, no cluster channel listening. The `cluster-server` module is built but listens only if `CLUSTER_BIND_ADDR` is configured.

## Failure Modes

| Failure                                            | Behavior                                                                                                                                                                      |
|----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source node fails during shard catch-up            | Target retries against another replica of the source shard. If `R=1` source, operator must restore source or accept partial loss for the in-flight move.                      |
| Target node fails during shard catch-up            | Source continues serving. Config entry stays pending until operator restarts target (resumes from persisted progress) or cancels the move.                                    |
| Target node fails after handoff but before cleanup | Source's data still exists (not yet `RangeDelete`d). Operator can re-propose a move or restart target.                                                                        |
| Network partition during move                      | Stream errors; target retries with backoff. If long-lived, operator cancels and re-issues from another replica.                                                               |
| Shard routed to a node that no longer owns it      | Node serves the shards it still read-owns and declines the rest (returning their current owners); coordinator re-dispatches only those. No request lost.                      |
| Apply on owner node fails for one shard            | `head(TxSuccess)` on that node doesn't advance past `T.t`. Other shards' apply on this node also halt (single-partition tx-log = serial apply per node). Operator-actionable. |
| Owner's head trails a sub-query's pinned `t`       | Owner waits for its local apply to reach `t` before opening iterators (bounded by apply lag; the transaction is already in the log). Coordinators prefer replicas already at `t`; if all replicas of a shard lag beyond the freshness threshold, wait briefly or `412` (operator policy). |
| Node partitioned from peers, tx log reachable      | Coordinators route reads to other replicas. The node keeps applying until the next transaction with a conditional clause on a `:patient-scoped` type; there its cross-shard search fan-out blocks, its head stops advancing, and the staleness watchdog eventually takes it out of read service. Resolves when connectivity returns. |
| Node apply lags or stalls while serving local-head reads | Reads keep serving at the stalled head — correct historical snapshots, increasingly stale. The staleness watchdog takes the node out of read service (`503`) once it has trailed the tx-log head longer than `MAX_READ_STALENESS`. Strong-mode deployments block instead (bounded wait, then error). |

The pattern: state lives in `ClusterConfig` (durable, replicated); moves are resumable via state-sync's existing resumability; worst case is operator-actionable with clear next steps; no silent data loss.

## Critical Files

**New modules:**

- `modules/cluster-server/` — HTTP/2 server on the cluster port. Handlers for `/cluster/query` (streaming sub-query with k-way per-shard merge), `/cluster/cql` (per-node CQL sub-query), `/cluster/body/...` (single materialized resource), `/cluster/include` / `/cluster/rev-include` (batched reference lookups), and `/cluster/state-sync` (per-shard source). Integrant components for the Jetty server with mTLS. The operator-facing `/__admin` endpoints (`cluster-config`, `shard-move`, `mode`) are *not* here — they are served by the existing public-facing server alongside v2's `/__admin/state-sync` and `/__admin/mode`.
- `modules/cluster-client/` — HTTP/2 client component used by every node when acting as coordinator. Connection pool per peer node; streaming response decoder; replica-selection logic (head-aware default — prefer replicas at the pinned `t`, see [Read Consistency](#read-consistency); consistent-hashing stickiness optional); per-shard routing validation with declined-shard re-dispatch.
- `modules/cluster-test-util/` — multi-JVM fixtures for the verification suite.

**New code in existing modules:**

- `modules/db/src/blaze/db/impl/codec/shard.clj` — shard function (`xxhash64 mod N`), constants.
- `modules/db/src/blaze/db/impl/codec/placement.clj` — static placement table (type → `:patient-root` / `:patient-scoped` / `:global-replicated`; primary-subject reference path per `:patient-scoped` type).
- `modules/db/src/blaze/db/impl/index/cluster_config.clj` — `ClusterConfig` CF reader/writer.
- `modules/admin-api/src/blaze/admin_api/cluster.clj` — operator-facing `/__admin/cluster-config` and `/__admin/shard-move` (propose / status / cancel) handlers, wired into the existing `/__admin` reitit-ring router in `blaze.admin-api`. Served by the public-facing server (not `cluster-server`), so the frontend and `blazectl` reach them under Blaze's existing admin auth.
- `modules/db/src/blaze/db/node/cluster.clj` — coordinator/owner routing; per-node sub-query dispatch; two-level merge.

**Heavily modified:**

- `modules/db/src/blaze/db/impl/index/*.clj` — every sharded CF gains the `shard-id` prefix in its key codec; iterators take a shard-bounded prefix.
- `modules/db/src/blaze/db/impl/index/resource_as_of.clj` — value gains `shard-id (2)`. Same for `type_as_of.clj`, `system_as_of.clj`.
- `modules/db/src/blaze/db/node/tx_indexer.clj` — apply path computes per-version `shard-id` from resolved subject, fans out per-shard WriteBatches per [Apply Pipeline](#apply-pipeline).
- `modules/db/src/blaze/db/node/tx_indexer/rewrite.clj` — conditional clause resolution dispatches cross-shard searches via cluster client; aggregates responses; same deterministic outcome on every node.
- `modules/db/src/blaze/db/cache/resource_cache.clj` — materialized-resource cache lives on owner replicas (unchanged key shape; placement determined by where the resource's apply runs).
- `modules/db-state-sync/` — adds `?shard=` parameter to source iterator and frame filter.
- `modules/interaction/src/blaze/interaction/*.clj` — FHIR REST handlers gain coordinator behaviour: resolve shard locally, dispatch to owner(s) via `cluster-client`, materialize.
- `modules/cql/src/blaze/cql/...` — owner-local per-shard patient enumeration.
- `modules/operation-measure-evaluate-measure/src/blaze/fhir/operation/evaluate_measure/...` — coordinator side: per-node sub-query dispatch, partial-MeasureReport merge, derived-value computation, subject-list `List` creation; owner side: measure evaluation over an enumerated patient set.

**Build matrix:**

- `.github/workflows/build.yml` — add `cluster-client`, `cluster-server`, `cluster-test-util` to the `module` matrix (sorted alphabetically).

**Docs:**

- `docs/deployment/environment-variables.md` — new `CLUSTER_*` variables; `READ_CONSISTENCY` (`local` default | `strong`) and `MAX_READ_STALENESS` (ISO-8601 duration, e.g. `PT10S`).
- `docs/deployment/sharding.md` — operator-facing runbook (deploy topology, add/remove nodes, shard moves, monitoring). New.
- Existing `docs/implementation/database.md` updated to point at this doc and to note the sharding-ready CF layout.

## Build Sequence

Suggested order so each step lands behind a green test suite. v2 core storage is assumed already in flight or done; sharding follows.

1. **Shard codec + placement table.** `shard.clj`, `placement.clj`. Standalone unit tests. No callers yet.
2. **`shard-id` in `ResourceAsOf` / `TypeAsOf` / `SystemAsOf` values.** Add the field; default to `0xFFFF` on every existing row. v2 callers see no behavioral difference.
3. **`shard-id` prefix on sharded CFs.** Add the prefix; default to `0x0000`. Iterators take a shard-bounded prefix. v2 callers see no behavioral difference at `N=1`.
4. **Per-version `shard-id` computation at apply.** `tx_indexer.rewrite` computes `shard-id` from the resolved subject. Stored in `ResourceAsOf.value`. At `N=1` always equals `0x0000` (or `0xFFFF` for `:global-replicated`); at `N>1` distributes per the placement rule.
5. **`ClusterConfig` CF + admin endpoints (read).** Default content: single-node, single-shard. Endpoints serve current config.
6. **Cluster channel — `cluster-server` skeleton + `cluster-client`.** HTTP/2 + mTLS; `/cluster/query` stub returning fixed responses for testing; ping endpoint. Wired through Integrant.
7. **Coordinator/owner routing for single-shard reads.** `GET /Type/{id}` resolves shard locally, dispatches via `cluster-client`. Owner serves via existing FHIR handlers. At `M=1` the dispatch is local; the round-trip is exercised in integration tests with a multi-JVM fixture.
8. **Scatter-gather for type search.** Per-node sub-query dispatch; per-node `shards` list; two-level k-way merge. Streaming with HTTP/2 back-pressure.
9. **Pagination across shards.** Continuation token with per-node per-shard cursors; stable result across pages.
10. **Cross-shard `_include` / `_revinclude`.** Coordinator post-processing for cross-shard target fetches.
11. **CQL fan-out.** `/cluster/cql` endpoint; owner-local patient enumeration; per-node sub-query dispatch; partial-MeasureReport merge. Per-shard Bloom-filter cache.
12. **Cross-shard conditional clause resolution.** Apply path's rewrite stage dispatches cross-shard searches; aggregates; same outcome on every node.
13. **Shard moves via state-sync per shard.** `/cluster/state-sync?shard=` source; importer per-shard; admin endpoints for propose / status / cancel; config-change transaction commits handoff atomically.
14. **Node modes (`:joining`, `:draining`).** Admin endpoints; mode-aware request handling.
15. **Standalone-to-distributed growth.** End-to-end: deploy `M=1, N=256`; add second node; move shards; verify.
16. **Microbenchmarks vs v2 unsharded baseline.** `modules/db/perf/` extensions for the cluster paths.
17. **Operator runbook + deployment docs.** `docs/deployment/sharding.md`; update `database.md` and `environment-variables.md`.

## Verification

End-to-end tests live in `modules/db/test/`, `modules/cluster-server/test/`, `modules/cluster-client/test/`, and `modules/interaction/test/`. Multi-node tests run multiple JVMs via the `cluster-test-util` fixture sharing a tx-log and globally-replicated metadata while owning disjoint sharded data.

### Determinism

1. **Per-version shard determinism.** Drive the same transaction sequence (creates, puts, deletes, subject changes, conditional updates, `Patient/$merge` equivalents) through two independent clusters with identical topology. Assert byte-identical content of every sharded and globally-replicated CF on every node.
2. **Internal-id minting cross-node determinism.** Same sequence with at least one phantom-then-create and at least one bundle-internal forward reference. Assert identical id allocations and identical `ResourceAsOf.shard-id` on every node.
3. **Conditional clause cross-shard resolution.** Transaction with conditional reference whose match candidates span shards. Assert every node fans out, aggregates, and reaches the same outcome.

### Locality invariant

4. **Primary-compartment query is RPC-free.** CQL `Patient`-context library whose retrieves resolve only within each patient's compartment or to `:global-replicated` types (e.g., `[Observation: code in valueset] where Observation.subject = Patient`, joined with `Medication` and `Practitioner` references). Run against a population spread across all shards. Instrument cluster-client RPC counters. Assert zero outgoing RPCs per shard during evaluation.
5. **Secondary-membership compartment query crosses shards.** `Observation` with `subject = Patient/A` and `performer = Patient/Y` on different shards. Query `Patient/{Y}/Observation`. Assert local compartment index hit, cross-shard body fetch from `shard(A)`, correct result.

### Subject changes and `$merge`

6. **Subject change per-version placement.** Create with `subject=A`, then PUT with `subject=B`. Assert v1 on `shard(A)`, v2 on `shard(B)`, compartment-A entry for v1 only, compartment-B entry for v2 only, `ResourceAsOf` carries per-version `shard-id`.
7. **`Patient/$merge` cross-shard.** Source patient on `shard(A)`, target on `shard(B)`. Assert every clinical resource's next version is placed on `shard(B)`; source compartment query returns empty (filtered by num-changes mismatch); target compartment query returns merged-in resources.
8. **Stale-compartment-entry filter.** After subject change A→B, query `Patient/{A}/Observation`. Assert zero results.

### CQL fan-out

9. **CQL aggregate count.** 1M synthetic patients across 256 shards. Assert cluster count matches unsharded baseline.
10. **CQL stratification merge.** Per-stratum counts match baseline.
11. **Owner-local patient enumeration parity.** The union of the per-shard patient sets enumerated across all owners equals the cluster's patient set at the pinned `t` — no duplicates, no gaps; phantoms and deleted patients excluded.
11a. **Subject-list report merge.** `reportType=subject-list` across shards: the merged `List` contains every qualifying subject exactly once; report and `List` match the unsharded baseline.
12. **Per-shard Bloom-filter cache.** Each shard builds its own filter; second-run latency drops per-shard independently.

### Search and pagination

13. **Type search scatter-gather.** Result Bundle equals baseline.
14. **Type search with `_sort` stable across pages.** Collected pages match sorted baseline; continuation token resumes correctly.
15. **Type search `_total=accurate`.** Returned count equals sum of per-shard counts.
16. **Pagination with streaming cancellation.** Closing coordinator stream propagates to nodes; no iterator leaks.
17. **Cross-shard `_include`.** Result Bundle contains all included resources.
18. **Cross-shard `_revinclude`.** Result Bundle contains all reverse-includes.

### Shard moves

19. **Shard move bit-identical end state.** Move shard 42 from A to B. Assert B's data matches A's pre-move byte-for-byte; A holds no rows with `shard-id = 42` post-move.
20. **Shard move handoff atomicity.** Write traffic during move. Assert every transaction lands either entirely on A (pre-handoff) or entirely on B (post-handoff); no split-state transactions.
21. **Shard move resumability.** Kill target mid-stream; restart; assert final state matches single-pass move.
22. **Concurrent independent shard moves.** Two moves of distinct shards in parallel. Both complete.
23. **Concurrent conflicting shard moves rejected.** Two moves of the same shard: second returns `409 conflict` with config version.
24. **Standalone-to-distributed growth.** Start `M=1, N=256, R=1`. Add second node. Move 128 shards. Assert correct routing and atomic writes; same data inventory.

### Config and consistency

25. **Stale-routing re-dispatch.** Route a sub-query for a moved shard to its former owner; assert the owner serves its still-owned shards, declines the moved shard (reporting the current owner), and the coordinator re-dispatches only that shard to the new owner. Assert an unrelated `ClusterConfig.version` bump triggers no re-dispatch.
26. **Cross-shard read consistency at the coordinator's `t`.** Owners deliberately lagging; scatter-gather search pins the coordinator's head `t`; lagging owners sync to `t` before serving; result equals historical baseline at `t`.
26a. **Head-aware replica selection and freshness backstop.** One replica of a shard held behind `t`: the coordinator picks the caught-up replica, no wait. All replicas held behind `t` beyond the freshness threshold: the request waits briefly or returns `412`, per policy.
27. **Paginated stable snapshot.** Page 1 at pinned `t`; advance cluster; page 2 reflects `t` not latest.
27a. **Session token (`Prefer: blaze-min-t`).** Write through node A; read through node B carrying the write's `t` from the `ETag`: B waits until its head reaches `t`, the read sees the write, `Preference-Applied` is returned, and no tx-log access occurs (instrumented). A `t` from the future: bounded wait, then `412`. vread of the write's `Location` through node B succeeds with no token.
27b. **Strong mode.** `READ_CONSISTENCY=strong`: write through node A; an immediate token-less read through node B sees the write.
27c. **Staleness watchdog.** Stall one node's apply while writes continue; its reads return `503` once the staleness bound is exceeded; reads recover when apply resumes.

### Performance

28. **Microbenchmarks.** Targets:
    - Compartment query latency ≤ unsharded baseline (ideally improved by smaller per-shard working sets).
    - Type search throughput ≥ 2× baseline on `M=4` (per-node parallelism).
    - CQL aggregate throughput ≥ `M × 0.8` baseline (near-linear scaling).
    - Disk per node ≤ `(R/M) × baseline-disk + ~150 GB` globally-replicated metadata (for billion-resource projection).
    - Apply throughput equal to unsharded v2 (single-partition tx-log ceiling).
29. **Bulk-import scaling.** 1M-resource Bundle spanning every shard; Layer 1 distributes across nodes; total apply time ≤ `1/M × single-node baseline`.

### Standalone parity

30. **`N=1` standalone parity with v2.** Run the full v2 test suite against `N=1` standalone. Assert zero behavioral difference vs unsharded v2.
31. **`N=256` standalone parity.** Same v2 test suite against `N=256, M=1, R=1`. Same FHIR-surface behavior; verify parallel-iteration speedup on type searches microbenchmark.

### Coverage and CI gates

32. **Coverage.** `make test-coverage` ≥ 95% forms across `db`, `cluster-server`, `cluster-client`, `interaction`.
33. **CI matrix.** `.github/workflows/build.yml` `module` matrix includes `cluster-client`, `cluster-server`, `cluster-test-util`.

Standard CI gates: `make fmt`, `make lint`, per-module test, multi-module integration tests via `cluster-test-util`.

## Open Items (deferred)

### Performance — caches and lookups

- **Replica stickiness via consistent hashing on `internal-id`.** Default replica selection is head-aware among replicas caught up to the pinned `t`; a `hash(internal-id) mod R` selector would additionally pin resources to a primary replica, doubling cache concentration. Operator config flag.
- **Per-instance shard sets for multi-compartment-by-design types.** Today `Communication`, `AuditEvent` etc. are `:global-replicated`. A future `ResourceAsOf.shard-id` as a *set* of shards would let high-fan-out types replicate to exactly the involved compartments. Storage win for `AuditEvent`-heavy deployments. Adds value-shape complexity.

### Performance — apply path

- **Leader-based conditional-clause resolution.** A deterministic leader-per-transaction fans out cross-shard searches and broadcasts the resolution. Cuts cluster-wide RPC by `M×` for transactions with conditional clauses at the cost of a sync barrier on those transactions. Build if conditional-clause traffic dominates apply CPU.
- **Reference-collection / search-param indexing pass fusion.** Inherited from v2 open items.
- **`prepare-state-sync` pre-build on v1.** Inherited from v2; same scope.

### Storage — alternative layouts

- **CF-per-shard alternative to shard-id prefix.** Each shard's data in its own RocksDB column family. `DropColumnFamily` makes shard moves near-instant; per-CF compaction is cleaner. Trade is per-CF fixed overhead at scale (~2000 CFs on a node owning all 256 shards). Revisit if shard-move latency becomes the bottleneck.
- **2-byte densely-assigned `c-hash` for search-param codes.** Inherited from v2; saves 2 bytes per row × every sharded CF — larger win in the sharded design due to the additional shard-id prefix.

### Operations — automation

- **Auto-rebalancer.** v1 is operator-driven. An auto-rebalancer monitors distribution and proposes moves to balance load. Lives as a separate Integrant component with leader election.
- **Coordinated shard-move scheduling.** A scheduler serializing per-node-bandwidth across concurrent moves smooths source-node contention.
- **Live `RF` tuning.** Single `/__admin/replication-factor` endpoint that automates the move sequence. Operator quality-of-life.

### Operations — observability

- **Per-shard metrics.** Apply latency, sub-query latency, body-fetch latency, cache hit rate, sliced by `shard-id`.
- **Cross-shard request tracing.** OpenTelemetry integration at the cluster-client level.
- **Shard distribution dashboard.** Frontend visualization of `ClusterConfig.assignment` and in-progress moves.

### Protocol / topology

- **External coordination service for `ClusterConfig`.** Pluggable source (etcd, Consul, K8s API) for dynamic environments.
- **Rack-aware / zone-aware replica placement.** Node-attribute-based `RF` constraints (e.g. "at least K racks").
- **Tunable read consistency per request.** `Prefer: handling=eventual-consistency` header to let each shard serve at its local head, accepting torn cross-shard snapshots for latency.
- **Shard-id width.** 2 bytes accommodates `N ≤ 65535`. Larger `N` would need format expansion; documented as the format boundary.

### Carry-overs from v2 unchanged

- RocksDB value-size limits for the `Resource` CF (multi-MB embedded base64). Same scope as v2.
- Blob-store janitor based on Kafka consumer-group offsets. Same as v2.
- `--history=live-only` state-sync opt-out. Same as v2; applies to per-shard moves as well as cluster-wide bootstrap.

### Open design questions

- **Coordinator-side matcher evaluation.** `matches?` / `matcher-transducer` have no coordinator-side consumer today (see [Matchers](#matchers)); if one appears, decide between owner-side evaluation (ship handle + clauses) and body-based matching (evaluate clauses against the materialized body).
- **Conditional clause cross-shard fan-out cost in practice.** Real workload data on conditional-clause prevalence would settle whether independent fan-out or leader-based should be the default.
- **Compartment-index stale-entry cleanup as a background task.** Per the per-version placement rule, stale entries filter at read time via num-changes equality. A periodic compaction-time filter (drop rows whose num-changes is dominated by a later row for the same internal-id) would reclaim space for `$merge`-heavy deployments.
- **Coordinator-side connection pool sizing.** Each node's cluster-client maintains HTTP/2 connections to every peer. With high `M`, per-node pool tuning becomes a follow-up.

## References

- **\[Terry 1994\]** Douglas B. Terry, Alan J. Demers, Karin Petersen, Mike J. Spreitzer, Marvin M. Theimer, Brent W. Welch: *Session Guarantees for Weakly Consistent Replicated Data.* Proceedings of the Third International Conference on Parallel and Distributed Information Systems (PDIS), Austin, TX, September 1994, pp. 140–149. IEEE Computer Society. [doi:10.1109/PDIS.1994.331722](https://doi.org/10.1109/PDIS.1994.331722)
- **\[Brewer 2000\]** Eric A. Brewer: *Towards Robust Distributed Systems.* Keynote, 19th ACM Symposium on Principles of Distributed Computing (PODC), Portland, OR, July 2000. [doi:10.1145/343477.343502](https://doi.org/10.1145/343477.343502)
- **\[Gilbert 2002\]** Seth Gilbert, Nancy Lynch: *Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services.* ACM SIGACT News 33(2), June 2002, pp. 51–59. [doi:10.1145/564585.564601](https://doi.org/10.1145/564585.564601)
- **\[Abadi 2012\]** Daniel J. Abadi: *Consistency Tradeoffs in Modern Distributed Database System Design: CAP is Only Part of the Story.* IEEE Computer 45(2), February 2012, pp. 37–42. [doi:10.1109/MC.2012.33](https://doi.org/10.1109/MC.2012.33)
- **\[RFC 7240\]** James M. Snell: *Prefer Header for HTTP.* IETF RFC 7240, June 2014. <https://www.rfc-editor.org/rfc/rfc7240>
- **\[RFC 8742\]** Carsten Bormann: *Concise Binary Object Representation (CBOR) Sequences.* IETF RFC 8742, February 2020. <https://www.rfc-editor.org/rfc/rfc8742>
- **\[Datomic\]** Nubank / Cognitect: *Datomic documentation* — the `sync` API and basis-`t` time model. <https://docs.datomic.com/>
