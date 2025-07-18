# Database Implementation

## Overview

The database architecture of Blaze is influenced by [Datomic][1] and [XTDB][2]. Both databases have a strong foundation in functional programming and [persistent data structures][3] leading to immutable databases.

## Immutable Database

The idea behind an immutable database is, that the whole database content at a point in time acts like one big immutable value. The database itself evolves over time by transitioning database values through transactions. The time is modelled explicitly, assigning each database value a value `t` (logical timestamp) which increases monotonously.

```
+-------------+                   +-------------+
| Value (t=1) | -> Transaction -> | Value (t=2) |
+-------------+                   +-------------+
```

Database values are not copied entirely from one version to the next. Instead, like in persistent data structures, structural sharing is used. As such each database value can be seen as a complete copy of the database from the outside, but at the inside, the implementation is efficient enough to be feasible nowadays.

**Note:** In contrast, relational databases, which where designed in the 80's, use an update in-place model, because storage was expensive then.

A similar technique is [copy-on-write][4], used in many areas of computing, like modern filesystems.

Having a database architecture which consists of immutable database values evolving over time, where past values are kept either forever or at least sufficiently long, has the property that reads don't need coordination. Because database values can be referenced by `t`, an arbitrary number of queries, spread over arbitrary periods of time, can access the same immutable snapshot of the database. In case data is replicated over multiple nodes, queries can even run in parallel, all accessing the same coherent database value.

As one example, in paging of FHIR searchset or history bundles, Blaze simply refers to the timestamp value `t` of the first page, in order to calculate every remaining page based on the same stable database value.

In practise, each FHIR RESTful API read request will obtain the newest known database value and use that for all queries necessary to answer the request.

## Logical Data Model

Datomic uses a fact based data model, where each fact is a triple `(entity, attribute, value)` for example `(<patient-id>, birthDate, 2020)`. This model has one big advantage, the minimum change between two database values will be a fact, which is quite small. The disadvantage is, that bigger structures like resources have to be reconstructed from individual facts. In addition to that, because in FHIR, updates are always whole resources, the actual changed facts have to be determined by diffing the old and new resource.

Another data model is the document model used in document stores like [MongoDB][5] but also XTDB. With documents, the smallest structure in the database is a document. Blaze uses a document data model where each resource version is a document. Because Blaze uses resource versions as documents, instead of only the current version, documents can't be indexed by resource identifier. Instead, as XTDB does, content hashes will be used. Aside from the version based document store, several indices are used to build up the database values and enable queries.

## Indices

There are two different sets of indices - the first set depends on a database value at time `t` and the second set depends only on the content hash of resource versions. The former set of indices is used to build up the database values, whereas the latter set is used to enable queries based on search parameters.

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

The `ResourceAsOf` index is the primary index which maps the resource identifier `(type, id)` together with the `t` to the `content-hash` of the resource version. In addition to that, the index contains the number of changes `num-changes` to the resource, the operator `op` of the change leading to the index entry and an optional `purged-at` point in time were the version was purged.

The `ResourceAsOf` index is used to access the version of a resource at a particular point in time `t`. In other words, given a point in time `t`, the database value with that `t`, allows to access the resource version at that point in time by its identifier. Because the index only contains entries with `t` values of changes to each resource, the most current resource version is determined by querying the index for the greatest `t` less or equal to the `t` of the database value.

Index entries with a `purged-at` point in time at or before the current `t` of a database are not part of that database. 

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

In addition to a direct resource lookup, the `ResourceAsOf` index is used for listing all versions of a particular resource, listing all resources of a particular type and listing all resources (of all types). Listings are done by scanning through the index and for the non-history case, skipping versions not appropriate for the `t` of the database value. 

#### TypeAsOf

The `TypeAsOf` index contains the same information as the `ResourceAsOf` index with the difference that the components of the key are ordered `type`,  `t` and `id` instead of `type`, `id` and `t`. The index is used for listing all versions of all resources of a particular type. Such history listings start with the `t` of the database value going into the past. This is done by not only choosing the resource version with the latest `t` less or equal the database values `t` but instead using all older versions. Such versions even include deleted versions because in FHIR it is allowed to bring back a resource to a new life after it was already deleted. The listing is done by simply scanning through the index in reverse. Because the key is ordered by `type`,  `t` and  `id`, the entries will be first ordered by time, newest first, and second by resource identifier.

#### SystemAsOf

In the same way the `TypeAsOf` index uses a different key ordering in comparison to the `ResourceAsOf` index, the `SystemAsOf` index will use the key order `t`, `type` and `id` in order to provide a global time axis order by resource type and by identifier secondarily.

#### PatientLastChange

The `PatientLastChange` index contains all changes to resources in the compartment of a particular Patient on reverse chronological order. Using the `PatientLastChange` index it's possible to detect the `t` of the last change in a Patient compartment. The CQL cache uses this index to invalidate cached results of expressions in the Patient context. 

#### TxSuccess

The `TxSuccess` index contains the real point in time (as `java.time.Instant`) of a successful transaction. In other words, this index maps each `t` which is just a monotonically increasing number to a real point in time.

**Note:** Blaze is not bitemporal, like XTDB is. This means that the time recorded in the history of resources is the transaction time, not a business time. This also means that one can't fix the history, because the history only reflects the transactions happened.

#### TxError

The `TxError` index will keep track of all failed transactions, storing the anomaly about the failure reason. It is used to be able to return this anomaly as result of failing transactions.

#### TByInstant

The `TByInstant` index is used to determine the `t` of a real point in time. This functionality is needed to support the `since` parameter in history queries.

#### TypeStats

The `TypeStats` index keeps track of the total number of resources, and the number of changes to resources of a particular type at a particular point in time `t`. This statistic is used to populate the total count property in type listings and type history listings in case there are no filters applied.

#### SystemStats

The `SystemStats` index keeps track of the total number of resources, and the number of changes to all resources at a particular point in time `t`. This statistic is used to populate the total count property in system listings and system history listings in case there are no filters applied.

### Search Param Indices

The indices not depending on `t` directly point to the resource versions by their content hash. 

| Name                                | Key Parts                                                      | Value |
|-------------------------------------|----------------------------------------------------------------|-------|
| SearchParamValueResource            | search-param, type, value, id, hash-prefix                     | -     |
| ResourceSearchParamValue            | type, id, hash-prefix, search-param, value                     | -     |
| CompartmentSearchParamValueResource | comp-code, comp-id, search-param, type, value, id, hash-prefix | -     |
| CompartmentResourceType             | comp-code, comp-id, type, id                                   | -     |
| SearchParam                         | code, type                                                     | id    |
| ActiveSearchParams                  | id                                                             | -     |

#### SearchParamValueResource

The `SearchParamValueResource` index is used to find resources based on search parameter values. It contains all values from resources defined in search parameters with the value followed by the resource id and hash. The components of its key are:

* `search-param` - a 4-byte hash of the search parameters code used to identify the search parameter
* `type` - a 4-byte hash of the resource type
* `value` - the encoded value of the resource reachable by the search parameters FHIRPath expression. The encoding depends on the search parameters type.
* `id` - the logical id of the resource
 * `hash-prefix` - a 4-byte prefix of the content-hash of the resource version

The way the `SearchParamValueResource` index is used, depends on the type of the search parameter. The following sections will explain this in detail for each type:

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

##### String

**TODO: continue...**

##### Token

Search parameters of type `token` are used for exact matches of terminology codes or identifiers potentially scoped by a URI which represents the code system or naming system.

In order to facilitate different forms of searches specified in the [FHIR Spec][6], the value is encoded in the following 4-ways:
* `code` - the code without system
* `system|code` - the code scoped by the system
* `|code` - the code if the resource doesn't specify a system
* `system|` - the system independent of the code, used to find all resources with any code in that system

After concatenation, the strings are hashed with the [Murmur3][7] algorithm in its 32-bit variant, yielding a 4-byte wide value. The hashing is done to save space and ensure that all values are of the same length.

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

The `ResourceSearchParamValue` index is used to decide whether a resource contains a search parameter based value. It contains all values from resources defined in search parameters with the resource id and hash followed by the value. The components of its key are:

* `type` - a 4-byte hash of the resource type
* `id` - the logical id of the resource
* `hash-prefix` - a 4-byte prefix of the content-hash of the resource version
* `search-param` - a 4-byte hash of the search parameters code used to identify the search parameter
* `value` - the encoded value of the resource reachable by the search parameters FHIRPath expression. The encoding depends on the search parameters type.

#### CompartmentSearchParamValueResource

The `CompartmentSearchParamValueResource` index is used to find resources of a particular compartment based on search parameter values.

#### CompartmentResourceType

The `CompartmentResourceType` index is used to find all resources that belong to a certain compartment. The components of its key are:

 * `comp-code` - a 4-byte hash of the compartment code, ex. `Patient`
 * `comp-id` - the logical id of the compartment, ex. the logical id of the Patient
 * `type` - a 4-byte hash of the resource type of the resource that belongs to the compartment, ex. `Observation`
 * `id` - the logical id of the resource that belongs to the compartment, ex. the logical id of the Observation

#### ActiveSearchParams

Currently not used.

## Transaction Handling

* a transaction bundle is POST'ed to one arbitrary node
* this node submits the transaction commands to the central transaction log
* all nodes (including the transaction submitter) receive the transaction commands from the central transaction log

### Transaction Commands

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

[1]: <https://www.datomic.com>
[2]: <https://xtdb.com>
[3]: <https://en.wikipedia.org/wiki/Persistent_data_structure>
[4]: <https://en.wikipedia.org/wiki/Copy-on-write>
[5]: <https://www.mongodb.com>
[6]: <https://www.hl7.org/fhir/search.html#token>
[7]: <https://en.wikipedia.org/wiki/MurmurHash>
