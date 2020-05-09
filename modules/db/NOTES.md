# Crux

## Crux Indices

attr, eid and content-hash key parts have a size of 21 bytes which is the size of a SHA1 (160 bit) plus one for the value type. Value key part is the whole value.

| Name | Key Parts | Value | seek-values | Doc |
|---|---|---|---|---|
| DocAttributeValueEntityValueIndex | attr val eid content-hash | - |
| DocAttributeValueEntityEntityIndex | attr val eid content-hash | - | eid, EntityTx | same as above only with value already known and entity free
| DocAttributeEntityValueEntityIndex | attr eid content-hash val | - | ? | TODO: Swap v and content-hash, so it has the same order as AVE
| DocAttributeEntityValueValueIndex | attr eid content-hash val | - | ? | TODO: Swap v and content-hash, so it has the same order as AVE
| EntityAsOfIndex | eid valid-time tx-time tx-id | content-hash | eid, EntityTx | |
| EntityHistoryRangeIndex | | |
| 

In Crux an entity can have multiple documents over time. Every time a new document is put, every attribute is indexed together with the content-hash of the document.

### EntityAsOfIndex Example

| eid-0 | valid-time-new |
| eid-0 | valid-time-old |
| eid-1 | valid-time-old |

# Blaze NG

## Features

### Versioned read of resources 

* I need to get to a particular version of a resource
* versionId could be the content-hash

### Normal read returning the last known Version of a Resource

* need to get the content-hash of a resource given a t
* can be done with the ResourceAsOf index

### Search 

* stable search also in recent past (at given t)
* 

## Principles

* don't optimize for deleted resources because deleting a resource is not common

## Indices

### Independent from t

| Name | Key Parts | Value |
|---|---|---|
| SearchParamValue | c-hash tid value id hash-prefix | |
| ResourceValue | tid id hash-prefix c-hash | values |
| CompartmentSearchParamValue | co-c-hash co-res-id sp-c-hash tid value id hash-prefix | |
| CompartmentResourceValue | co-c-hash co-res-id tid id hash-prefix c-hash value? | value? |
| ResourceType | tid id | - |
| CompartmentResourceType | co-c-hash co-res-id tid id | - |
| SearchParam | code tid | id |
| ActiveSearchParams | id | - |

### Depend on t

| Name | Key Parts | Value |
|---|---|---|
| TxSuccess | t | transaction |
| TxError | t | anomaly |
| TByInstant | inst-ms (desc) | t |
| ResourceAsOf | tid id t | hash, state |
| TypeAsOf | tid t id | - |
| SystemAsOf | t tid id | - |
| TypeStats | tid t | total, num-changes |
| SystemStats | t | total, num-changes |

We can make hashes in SearchParam indices shorter (4-bytes) because we only need to differentiate between the versions of a resource. The odds of a hash collision is 1 out of 10000 for about 1000 versions. In case of a hash collision we would produce a false positive query hit. So we would return more resources instead of less, which is considered fine in FHIR.

### SearchParamValue

The key consists of:

* c-hash - a 4-byte hash of the code of the search parameter
* v-hash - a 4-byte hash of the value build from [token rules](https://www.hl7.org/fhir/search.html#token)
* tid    - the 4-byte type id
* id     - the id of the resource
* hash   - the resource content hash

The total size of the key is 4 + 4 + 4 + id-size + 32 = 44 + id-size bytes.

The value consists of:

* value - the value to double check in case of value hash collisions

The key contains the id of the resource for two reasons, first we can skip to the next resource by seeking with max-hash, not having to test all versions of a resource against ResourceAsOf and second, going into ResourceAsOf will be local because it is sorted by id.

The SearchParamValue index is comparable to the AVET index in Datomic.

### ResourceValue

The ResourceValue index is comparable to the EAVT index in Datomic although it's actually more like a ETAV index which doesn't exist in Datomic.

### CompartmentResource

Lists all resources part of a compartment. For example all resources of a certain patient in the [Patient compartment][1].

### ResourceType

Lists all rids (resource ids) of a particular type. Is used to list all resources of a type.

Assigns each resource a version at t. The version is incremented from the previous version at the previous t for the particular resource. The initial version is 0. The version is encoded as a signed long. The instance version is used to calculate the total number of instance history entries for all time or since a particular t.

TODO: add prefix bloom here with tid and id

### TByInstant

Provides access to t's by instant (point in time). It encodes the instant as milliseconds since epoch as descending long from Long/MAX_VALUE so that TODO WHY???

### ResourceAsOf

The key consists of:

* tid - the 4-byte type id
* id  - the variable length id (max 64 byte)


### TxSuccess

### TxError

### TypeStats / SystemStats

total = number of non-deleted resources
num-changes = total number of changes (creates, updates, deletes)

## Search

The FHIR search parameters have different types. The search implementation depends on that types. The following sections describe the implementation by type.

### Date

The date search parameter type is used for the data types date, dateTime, instant, Period and Timing. The search is always performed against a range. Both the value given in the search and the target value in resources have either an implicit or an explicit range. For example the range of a date like 2020-02-09 starts at 2020-02-09T00:00:00.000 and ends at 2020-02-09T23:59:59.999.

By default the search is an equal search were the range of the search value have to fully contain the range of the target value. In addition to the equal search, other search operators are possible.


[1]: <https://www.hl7.org/fhir/compartmentdefinition-patient.html>
