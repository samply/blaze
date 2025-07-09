# Database Implementation

## Overview

The database architecture of Blaze is influenced by [Datomic][1] and [XTDB][2]. Both databases have a strong foundation in functional programming and [persistent data structures][3], leading to immutable databases.

## Immutable Database

The core concept behind Blaze's database is immutability. The entire database at a specific point in time is treated as a single, immutable value. The database evolves over time by transitioning from one immutable value to the next through transactions. Time is modeled explicitly, with each database value being assigned a logical timestamp `t` that increases monotonically.

```
+-------------+                   +-------------+
| Value (t=1) | -> Transaction -> | Value (t=2) |
+-------------+                   +-------------+
```

Instead of copying the entire database for each new version, Blaze uses structural sharing, a technique common in persistent data structures. This means that while each database value appears as a complete, independent snapshot from the outside, the underlying implementation is highly efficient.

> [!NOTE] 
> This contrasts with traditional relational databases, which were designed in an era of expensive storage and therefore use an update-in-place model.

A similar technique, [copy-on-write][4], is used in many areas of computing, including modern filesystems.

This immutable architecture has a significant advantage: reads do not require coordination. Because each database value can be referenced by its unique `t`, any number of queries can access the same immutable snapshot of the database over any period. When data is replicated across multiple nodes, queries can run in parallel, all accessing the same coherent database value.

For example, when paging through FHIR search results or history bundles, Blaze simply refers to the `t` of the first page to ensure that all subsequent pages are calculated against the same stable database value.

In practice, each FHIR RESTful API read request obtains the most recent database value and uses that for all queries necessary to fulfill the request.

## Logical Data Model

Blaze uses a document-based data model, similar to document stores like [MongoDB][5] and XTDB. In this model, each version of a FHIR resource is a document.

This is different from a fact-based model like Datomic's, where each fact is a triple of `(entity, attribute, value)`. While the fact-based model allows for very granular changes, it requires reconstructing larger structures like resources from individual facts. In FHIR, where updates are always whole resources, a document model is a more natural fit.

Because Blaze stores all versions of a resource, not just the current one, resources cannot be indexed solely by their logical ID. Instead, Blaze uses content hashes to identify each unique resource version. In addition to the versioned document store, several indices are used to build the database values and enable efficient queries.

## Indices

There are two main categories of indices in Blaze:

*   **Transaction Indices**: These indices depend on a database value at a specific time `t` and are used to construct the database values themselves.
*   **Search Param Indices**: These indices are independent of `t` and point directly to resource versions by their content hash, enabling efficient searching.

### Transaction Indices

| Name              | Key Parts | Value                                     |
|-------------------|-----------|-------------------------------------------|
| ResourceAsOf      | type id t | content-hash, num-changes, op, purged-at? |
| TypeAsOf          | type t id | content-hash, num-changes, op, purged-at? |
| SystemAsOf        | t type id | content-hash, num-changes, op, purged-at? |
| PatientLastChange | pat-id t  | -                                         |
| TxSuccess         | t         | instant                                   |
| TxError           | t         | anomaly                                   |
| TByInstant        | instant   | t                                         |
| TypeStats         | type t    | total, num-changes                        |
| SystemStats       | t         | total, num-changes                        |

#### ResourceAsOf

The `ResourceAsOf` index is the primary index for looking up resources. It maps a resource's identifier (`type`, `id`) and a logical timestamp `t` to the `content-hash` of that resource version. It also stores the number of changes (`num-changes`) to the resource, the operation (`op`) that created this version, and an optional `purged-at` timestamp.

This index is used to access the version of a resource at a particular point in time `t`. To find the most current version of a resource for a given `t`, the database queries the index for the greatest `t` less than or equal to the database's `t`.

Index entries with a `purged-at` timestamp at or before the current `t` of a database are not considered part of that database.

##### Example

The following `ResourceAsOf` index:

| Key (type, id, t) | Value (content-hash, num-changes, op) |
|-------------------|---------------------------------------|
| Patient, 0, 4     | -, 3, delete                          |
| Patient, 0, 3     | b7e3e5f8, 2, update                   |
| Patient, 0, 1     | ba9c9b24, 1, create                   |
| Patient, 1, 2     | 6744ed32, 1, create                   |

provides the basis for the following database values:

| t   | type    | id  | content-hash |
|-----|---------|-----|--------------|
| 1   | Patient | 0   | ba9c9b24     |
| 2   | Patient | 0   | ba9c9b24     |
| 2   | Patient | 1   | 6744ed32     |
| 3   | Patient | 0   | b7e3e5f8     |
| 3   | Patient | 1   | 6744ed32     |
| 4   | Patient | 1   | 6744ed32     |

The database value with `t=1` contains one patient with `id=0` and content hash `ba9c9b24`, because the second patient was created later at `t=2`. The index access algorithm will not find an entry for the patient with `id=1` on a database value with `t=1` because there is no index key with `type=Patient`, `id=1` and `t<=1`. However, the database value with `t=2` will contain the patient with `id=1` and additionally contains the patient with `id=0` because there is a key with `type=Patient`, `id=0` and `t<=2`. Next, the database value with `t=3` still contains the same content hash for the patient with `id=1` and reflects the update on patient with `id=0` because the key `(Patient, 0, 3)` is now the one with the greatest `t<=3`, resulting in the content hash `b7e3e5f8`. Finally, the database value with `t=4` doesn't contain the patient with `id=0` anymore, because it was deleted. As can be seen in the index, deleting a resource is done by adding the information that it was deleted at some point in time.

In addition to direct resource lookups, the `ResourceAsOf` index is used for listing all versions of a particular resource, all resources of a particular type, and all resources in the database. Listings are done by scanning through the index and for the non-history case, skipping versions not appropriate for the logical timestamp `t` of the database value.

#### TypeAsOf

The `TypeAsOf` index contains the same information as the `ResourceAsOf` index, but with a different key order: `type`, `t`, and `id`. This ordering is optimized for listing the history of all resources of a particular type in reverse chronological order.

#### SystemAsOf

Similarly, the `SystemAsOf` index uses the key order `t`, `type`, and `id` to provide a global, time-ordered view of all resources in the database.

#### PatientLastChange

The `PatientLastChange` index tracks all changes to resources within a patient's compartment in reverse chronological order. This allows for efficient detection of the last change in a patient's compartment, which is used by the CQL cache to invalidate cached results.

#### TxSuccess

The `TxSuccess` index maps the logical timestamp `t` to the real-world `java.time.Instant` of a successful transaction.

> [!NOTE] 
> Blaze is not bitemporal like XTDB. The time recorded in the history of resources is the transaction time, not a business time. This means that the history reflects the sequence of transactions as they happened and cannot be altered.

#### TxError

The `TxError` index stores information about failed transactions, including the reason for the failure.

#### TByInstant

The `TByInstant` index is the reverse of `TxSuccess`, mapping a real-world `java.time.Instant` to a logical timestamp `t`. This is used to support the `_since` parameter in history queries.

#### TypeStats

The `TypeStats` index keeps track of the total number of resources and the number of changes for each resource type at a given `t`. This is used to efficiently populate the total count in type-level search and history queries.

#### SystemStats

The `SystemStats` index does the same as `TypeStats`, but for the entire system, providing total counts for system-level queries.

### Search Param Indices

These indices are independent of `t` and are used to find resources based on their content.

| Name                                | Key Parts                                                      | Value |
|-------------------------------------|----------------------------------------------------------------|-------|
| SearchParamValueResource            | search-param, type, value, id, hash-prefix                     | -     |
| ResourceSearchParamValue            | type, id, hash-prefix, search-param, value                     | -     |
| CompartmentSearchParamValueResource | comp-code, comp-id, search-param, type, value, id, hash-prefix | -     |
| CompartmentResourceType             | comp-code, comp-id, type, id                                   | -     |
| SearchParam                         | code, type                                                     | id    |
| ActiveSearchParams                  | id                                                             | -     |

#### SearchParamValueResource

The `SearchParamValueResource` index is the primary index for searching. It maps a search parameter and its value to the resources that contain that value. The key is composed of:

*   `search-param`: A 4-byte hash of the search parameter's code.
*   `type`: A 4-byte hash of the resource type.
*   `value`: The encoded value of the search parameter.
*   `id`: The logical ID of the resource.
*   `hash-prefix`: A 4-byte prefix of the resource's content hash.

The usage of this index depends on the search parameter's type.

##### Number

**TODO: continue...**

##### Date/DateTime

Search parameters of type `date` are used to search in data elements with date/time and period data types. All date types stand for an interval of a start point in time up to an end point in time. So date search uses interval arithmetic to find hits. For each value of a resource, the lower bound is encoded followed by the upper bound. Both bounds are encodes as numbers representing the seconds since epoch. UTC is used for local dates. The lower bound is separated by an null byte from the upper bound so that all resources are sorted by the lower bound first and the upper bound second.

For the different modifier, the search works the following way:

###### Equal (eq)

The value interval `v` has to intersect with the query interval `q`. In order to do so, the following must hold:
* the lower bound or upper bound of `v` is in `q`, or
* `v` completely encloses `q`.

We start by scanning through the index starting at the lower bound of `q` and ending at the upper bound of `q`. After that, we repeat the same process with the upper bound prefix, not adding any duplicates.

###### Less Than or Equal

**TODO: continue...**

###### Example

| search-param | type        | value                  | id | hash-prefix |
|--------------|-------------|------------------------|----|-------------|
| date         | Observation | 2025-01-01, 2025-01-31 | 1  | 6744ed32    |
| date         | Observation | 2025-02-01, 2025-02-31 | 2  | b7e3e5f8    |
| date         | Observation | 2025-03-01, 2025-03-31 | 1  | ba9c9b24    |

Search for `2025` results in the index handles `[1, [6744ed32]]`, `[2, [b7e3e5f8]]` and `[1, [ba9c9b24]]`. Note that the index handles are not distinct and not ordered.

##### String

**TODO: continue...**

##### Token

Search parameters of type `token` are used for exact matches of codes and identifiers. To support various search syntaxes, the value is encoded in four ways:

*   `code`: The code without system.
*   `system|code`: The code scoped by the system.
*   `|code`: The code if the resource doesn't specify a system.
*   `system|`: The system independent of the code, used to find all resources with any code in that system.

These strings are then hashed using the 32-bit [Murmur3][7] algorithm to create a fixed-length value, saving space and improving performance.

###### Example

For this example, we don't use the hashed versions of the key parts except for the content-hash.

| Key (search-param, type, value, id, content-hash) |
|---------------------------------------------------|
| gender, Patient, female, 1, 6744ed32              |
| gender, Patient, female, 2, b7e3e5f8              |
| gender, Patient, male, 0, ba9c9b24                |

In case one searches for female patients, Blaze will seek into the index with the key prefix (gender, Patient, female) and scan over it while the prefix stays the same. The result will be the `[id, hash]` tuples:
* `[1, 6744ed32]` and
* `[2, b7e3e5f8]`.

That tuples are further processed against the `ResourceAsOf` index in order to check whether the resource versions are valid regarding to the current `t`.

| search-param | type        | value       | id | hash-prefix |
|--------------|-------------|-------------|----|-------------|
| status       | Observation | preliminary | 1  | 6744ed32    |
| status       | Observation | preliminary | 1  | ba9c9b24    |
| status       | Observation | final       | 1  | b7e3e5f8    |

Search for `preliminary` results in an index handle of `[1 [6744ed32 ba9c9b24]]`. Search for `final` results in an index handle of `[1 [b7e3e5f8]]`. The union will give a index handle of `[1 [6744ed32 b7e3e5f8 ba9c9b24]]`.

##### Reference

**TODO: continue...**

##### Composite

**TODO: continue...**

##### Quantity

**TODO: continue...**

##### URI

**TODO: continue...**

##### Special

**TODO: continue...**

#### ResourceSearchParamValue

The `ResourceSearchParamValue` index is used to verify if a resource contains a specific search parameter value. Its key is ordered by resource (`type`, `id`, `hash-prefix`) first, then by search parameter and value.

#### CompartmentSearchParamValueResource

This index is used to find resources within a specific compartment that match a given search parameter value.

#### CompartmentResourceType

This index is used to find all resources of a specific type that belong to a given compartment. The components of its key are:

* `comp-code` - a 4-byte hash of the compartment code, ex. `Patient`
* `comp-id` - the logical id of the compartment, ex. the logical id of the Patient
* `type` - a 4-byte hash of the resource type of the resource that belongs to the compartment, ex. `Observation`
* `id` - the logical id of the resource that belongs to the compartment, ex. the logical id of the Observation

#### ActiveSearchParams

This index is currently not used.

## Transaction Handling

1.  A transaction bundle is POSTed to an arbitrary node.
2.  This node submits the transaction commands to a central transaction log.
3.  All nodes (including the submitting node) receive the transaction commands from the log and apply them to their local state.

### Transaction Commands

Blaze supports several transaction commands:

*   **`create`**: Creates a new resource.
*   **`put`**: Creates or updates a resource.
*   **`keep`**: An optimized `put` for cases where the resource is unlikely to have changed.
*   **`delete`**: Deletes a resource.
*   **`conditional-delete`**: Deletes resources based on search criteria.
*   **`delete-history`**: Deletes the history of a resource.
*   **`patient-purge`**: Removes all data associated with a patient.

### Create

The `create` command is used to create a resource.

#### Properties

| Name          | Required | Data Type     | Description                                     |
|---------------|----------|---------------|-------------------------------------------------|
| type          | yes      | string        | resource type                                   |
| id            | yes      | string        | resource id                                     |
| hash          | yes      | hash          | resource content hash                           |
| refs          | no       | list          | references to other resources                   |
| if-none-exist | no       | search-clause | will only be executed if search returns nothing |

### Put

The `put` command is used to create or update a resource.

#### Properties

| Name          | Required | Data Type     | Description                                 |
|---------------|----------|---------------|---------------------------------------------|
| type          | yes      | string        | resource type                               |
| id            | yes      | string        | resource id                                 |
| hash          | yes      | hash          | resource content hash                       |
| refs          | no       | list          | references to other resources               |
| if-match      | no       | number        | the t the resource to update has to match   |
| if-none-match | no       | "*" or number | the t the resource to update must not match |

### Keep

The `keep` command can be used instead of a `put` command if it's likely that the update of the resource will result in no changes. In that sense, the `keep` command is an optimization of the `put` command that has to be retried if it fails.

#### Properties

| Name     | Required | Data Type | Description                                                   |
|----------|----------|-----------|---------------------------------------------------------------|
| type     | yes      | string    | resource type                                                 |
| id       | yes      | string    | resource id                                                   |
| hash     | yes      | hash      | the resource content hash the resource to update has to match |
| if-match | no       | number    | the t the resource to update has to match                     |

### Delete

The `delete` command is used to delete a resource.

#### Properties

| Name       | Required | Data Type | Description                      |
|------------|----------|-----------|----------------------------------|
| type       | yes      | string    | resource type                    |
| id         | yes      | string    | resource id                      |
| check-refs | no       | boolean   | use referential integrity checks |

### Conditional Delete

The `conditional-delete` command is used to delete possibly multiple resources by selection criteria.

#### Properties

| Name           | Required | Data Type | Description                                      |
|----------------|----------|-----------|--------------------------------------------------|
| type           | yes      | string    | resource type                                    |
| clauses        | no       | string    | clauses to use to search for resources to delete |
| check-refs     | no       | boolean   | use referential integrity checks                 |
| allow-multiple | no       | boolean   | allow to delete multiple resources               |

### Delete History

The `delete-history` command is used to delete the history of a resource.

#### Properties

| Name       | Required | Data Type | Description                      |
|------------|----------|-----------|----------------------------------|
| type       | yes      | string    | resource type                    |
| id         | yes      | string    | resource id                      |

#### Execution

* get all instance history entries
* add the `t` of the transaction as `purged-at?` to the value of each of the history entries not only in the ResourceAsOf index, but also in the TypeAsOf and SystemAsOf index
* remove the number of history entries purged from the number of changes of `type` and thw whole system

### Patient Purge

The `patient-purge` command is used to remove all current and historical versions for all resources in a patient compartment.

#### Properties

| Name       | Required | Data Type | Description                      |
|------------|----------|-----------|----------------------------------|
| id         | yes      | string    | patient id                       |
| check-refs | no       | boolean   | use referential integrity checks |

## Queries

### Type Query

A type query retrieves all resources of a specific type that match a set of search parameter clauses. The execution of a type query is a two-phase process: query planning and query execution.

#### Query Planning

The goal of the query planning phase is to find the most efficient way to execute the query. This is done by splitting the search parameter clauses into two groups:

*   **SCANS**: A small set of clauses that will be used to scan an index.
*   **SEEKS**: The remaining clauses that will be used to filter the results from the scan.

The query planner tries to find the clause with the highest selectivity for the `SCANS` group. The selectivity of a clause is determined by the type of the search parameter and the values being searched for.

Generally, search parameters of type `token` are good candidates for `SCANS` because they often have a high selectivity. Other types like `date` or `quantity` are usually placed in the `SEEKS` group.

If a query contains multiple `token` search parameters, the query planner estimates the size of the index segment that needs to be scanned for each of them. The clause with the smallest estimated scan size is chosen for the `SCANS` group. Other `token` clauses might be added to the `SCANS` group if their estimated scan size is not much larger than the smallest one.

#### Query Execution

After the query planning is complete, the query is executed. The execution distinguishes between index scans and index seeks.

##### Index Scan

The query execution starts by scanning the `SearchParamValueResource` index for all **index handles** that match the clauses in the `SCANS` group. An index handle is a lightweight pointer to a resource, containing the resource ID and a collection of hash prefixes. This is a continuous read of a segment of the index, resulting in a stream of index handles.

###### Union and Intersection of Scans

The index scan becomes more complex if multiple values are supplied for one search parameter or if multiple search parameters are used for the scan.

*   **Union of Scans (Logical OR)**: If a search parameter in the `SCANS` group has multiple values (e.g. `(d/type-query db "Patient" [["gender" "male" "female"]])`), the query execution will perform an index scan for each value. The individual streams of index handles are then combined into a single stream by a **stream union** operation. The resulting stream contains all index handles from all scans, with duplicates removed.

*   **Intersection of Scans (Logical AND)**: If there are multiple search parameters in the `SCANS` group (e.g. `(d/type-query db "Observation" [["status" "final"] ["code" "..."]])`), the query execution will perform an index scan for each search parameter. The individual streams of index handles are then combined into a single stream by a **stream intersection** operation. The resulting stream contains only those index handles that are present in all scanned streams.

##### Index Seek

For each **index handle** from the index scan, the query execution will then check if it also matches the clauses in the `SEEKS` group. This is also done using index seeks, but instead of looking up values in the `SearchParamValue-Resource` index, it uses the `ResourceSearchParamValue` index to efficiently verify if a given resource (represented by its index handle) has the required values for the `SEEKS` clauses.

A scan is more performant than many individual seeks. Therefore, the query planner tries to minimize the number of seeks by selecting the most specific clause for the scan, which will produce the smallest number of index handles that need to be checked in the second phase.

Finally, the index handles that survive the filtering process are converted into full **resource handles**. A resource handle is a more detailed pointer to a resource version, containing not only the resource ID and hash, but also the logical timestamp `t` of that version. This conversion requires an additional seek into the `ResourceAsOf` index for each index handle. This final seek is crucial because it guarantees that the returned resources are consistent with the state of the database at the specific point in time (`t`) of the query.

#### Example

Consider the following query:

```clojure
(d/type-query db "Observation" [["status" "final"] ["code" "http://loinc.org|9843-4"]])
```

Both `status` and `code` are `token` search parameters. The query planner will estimate the number of Observation resources with `status=final` and the number of Observation resources with `code=http://loinc.org|9843-4`. Let's assume that there are far fewer observations with that specific LOINC code than observations with the status `final`.

The query plan will be:

*   **SCANS**: `code=http://loinc.org|9843-4`
*   **SEEKS**: `status=final`

The query execution will then:

1.  Scan the `SearchParamValueResource` index for all Observation **index handles** with `code=http://loinc.org|9843-4`.
2.  For each of these **index handles**, it will perform a seek in the `ResourceSearchParamValue` index to check if the resource has a `status` of `final`.
3.  The surviving **index handles** are then converted to **resource handles**.

This is much more efficient than scanning all observations with `status=final` and then checking the code for each of them.


[1]: <https://www.datomic.com>
[2]: <https://xtdb.com>
[3]: <https://en.wikipedia.org/wiki/Persistent_data_structure>
[4]: <https://en.wikipedia.org/wiki/Copy-on-write>
[5]: <https://www.mongodb.com>
[6]: <https://www.hl7.org/fhir/search.html#token>
[7]: <https://en.wikipedia.org/wiki/MurmurHash>
