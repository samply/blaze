# Value Set Expansion outside of the Database

## Intent

The `in` modifier of token search parameters, as in `Observation?code:in=<ValueSet URL>`, matches resources with a code from a value set. The codes are the result of expanding the value set with the terminology service. That result can change over time: a value set or code system can change, an external terminology server can answer differently, expansions are cached for some time, and the local terminology service reads value sets from the newest database.

The database has to be deterministic: a query against a database value has to return the same resources whenever it runs. As long as the database expands value sets itself, that doesn't hold:

* **Transaction log replay.** The indexer runs the conditions of conditional creates and deletes. A replay of the transaction log, on a new node or after an index rebuild, can get another expansion and so another outcome than the original run. A replay right at startup can even fail, because the local terminology service isn't available before all components are started.
* **Paging.** Every page of a search compiles its query again and so expands its value sets again. If the expansion changes between pages, later pages can miss resources of the original result or contain resources outside of it.

## Goals

* The database doesn't depend on a terminology service. Its query results and the outcome of indexing a transaction depend only on the database itself.
* All pages of a search use the same expansion.
* The conditions of conditional creates and deletes have the same outcome on every node and on every replay.
* Query compilation becomes synchronous again, because value set expansion was its only asynchronous step.
* Clients keep seeing the clauses as they wrote them, for example in the self link of a search.

## Non-Goals

* Indexing transaction log entries of earlier versions whose conditions hold value sets. We assume that the `in` modifier hasn't been used in conditional creates or deletes yet.
* Indices that speed up value set searches, like an index of the value sets the codes of a resource belong to.
* Limiting the number of codes of searches, CQL retrieves and `$graph` links.

## Approaches

1. **Expand in the database, but pin the expansion.** The database keeps expanding value sets, but caches expansions per value set for the lifetime of a search or a transaction. That doesn't help a replay, which runs long after the original expansion, and keeps the database dependent on the terminology service.
2. **Expand before calling the database (chosen).** The node receiving a request expands value sets into plain token clauses and passes only those to the database. Conditional creates and deletes store their expanded clauses in the transaction log, so indexing no longer needs a terminology service. Searches keep their expanded clauses in the page store.
3. **Record expansions in the database.** The node receiving a request stores the expansion in the database within the transaction, and queries read it from there. That would make expansions deterministic for later queries as well, but needs a new kind of data in the database and a rule for when an expansion is stored again.

Approach 2 was chosen, because it removes the dependency of the database on the terminology service with the least new machinery and keeps approach 3 possible later for indices (see Future Value Set Indices).

## Design

### Value Set Expander

A new component, the value set expander (`modules/value-set-expander`), replaces every clause with a token search parameter and the `in` modifier by a plain token clause of the same search parameter. That clause holds the distinct `system|code` values of all value sets of the clause in sorted order. Sorting makes equal expansions produce equal clauses, so they share page store tokens and expression cache keys. Chained search parameters are expanded as well. Clauses with the `in` modifier on search parameters of other types are kept, so that the database rejects them as usual.

Every expanded clause carries the clause it was expanded from in its metadata, so that callers can show the clauses as the client wrote them.

Expansions are cached by the URL of the value set. The cache holds up to 1000 expansions and refreshes an expansion one hour after it was loaded, like the per search parameter cache of the database did before. Failed expansions aren't cached.

Because the database no longer expands value sets, the local terminology service can depend on the database node directly, instead of getting it only after all components are started.

### Callers

* **Searches** expand the clauses after decoding their params. The expanded clauses are kept in the page store, also for searches issued via GET, and the paging links refer to them by a token derived from their SHA-256 hash. Next to the token, the page id carries the clauses as the client wrote them, so that the self link of every page shows the `in` modifier as given.
* **Conditional creates and deletes**, also in transaction and batch bundles, expand their conditions before submitting the transaction, so that the transaction log holds the expanded clauses.
* **CQL retrieves** expand their value sets at compile time. The forms of expressions, which are the keys of the expression cache, hold the codes of the expansion, so that a Bloom filter created with an outdated expansion isn't used. The display form shows the value sets unexpanded.
* **`$graph` links** expand the value sets of their link params once per request, so that all source resources use the same expansion.

### Empty Value Sets

A clause whose value sets all expand to no codes can't become a plain token clause, because such a clause needs at least one value. No resource can match it.

* Searches drop such a clause from a disjunction. If a clause or a disjunction can't match any resource, the search answers with an empty bundle without querying the database.
* CQL retrieves and `$graph` links return no resources.
* Conditions of conditional creates and deletes fail with a `422 Unprocessable Entity`, because a condition that can never match is most likely not what the client intended.

### Large Value Sets

The conditions of conditional creates and deletes are stored in the transaction log together with their codes. A large value set would make a single record of the transaction log too large, for example larger than the default maximum message size of 1 MB of Kafka, and a bulk load would store its codes once per command. So the value sets of a condition can expand to at most 10,000 codes in total. A condition with more codes fails with a `409 Conflict` and the issue code `too-costly`, like other requests whose costs are limited.

### Guards in the Database

The database rejects value sets wherever they show up, so that no caller can bypass the expansion:

* **Queries:** The database doesn't support the `in` modifier on token search parameters. It's rejected when the values of a clause are compiled, not when the modifier is validated, so that lenient compilation, which drops clauses with invalid modifiers, doesn't drop the clause and return too many resources.
* **Transactions:** `d/transact` rejects conditional creates and deletes whose clauses hold a value set, before anything is written.
* **Indexing:** Transaction log entries written by earlier versions can still hold value sets. The indexer can't index such an entry without expanding the value set, so it fails the node with a message naming the transaction, instead of indexing it with an outcome that may differ from the original run.

### Future Value Set Indices

Moving the expansion out of the database doesn't rule out indices that speed up value set searches. To stay deterministic, such an index has to be built from data in the database, not from terminology service calls during indexing. A component outside the database would create the index through the database API, passing the expansion it is built from, and the database would record the existence of the index in a transaction. The expansion step could then check whether such an index exists in the database a query runs against and use it instead of the codes.
