# Changelog

## v0.15.6

### Other

* Update Dependencies ([#603](https://github.com/samply/blaze/pull/603))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/29?closed=1).

## v0.15.5

### New Features

* Implement FHIRPath Function extension ([#598](https://github.com/samply/blaze/pull/598))

### Bugfixes

* Fix NPE in Reference Resolution ([#599](https://github.com/samply/blaze/pull/599))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/28?closed=1).

## v0.15.4

### Bugfixes

* Consume the Whole Inputstream of Request Payloads ([#594](https://github.com/samply/blaze/pull/594))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/27?closed=1).

## v0.15.3

### Security

* Update Dependencies ([#585](https://github.com/samply/blaze/pull/585))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/26?closed=1).

## v0.15.2

### Security

* Update Google Protobuf to v3.19.2 ([#583](https://github.com/samply/blaze/pull/583))

### Other Improvements

* Enhance CQL Implementation ([#582](https://github.com/samply/blaze/pull/582))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/25?closed=1).

## v0.15.1

### Security

* Fix CVE-2021-3712 in Package openssl-libs 1:1.1.1k-4.el8 ([#574](https://github.com/samply/blaze/pull/574))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/24?closed=1).

## v0.15.0

### Operation

* Allow Setting the Database Sync Timeout ([#566](https://github.com/samply/blaze/pull/566))

### Bugfixes

* Ensure Error Response on Invalid Value in FHIR Search ([#563](https://github.com/samply/blaze/pull/563))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/20?closed=1).

## v0.14.1

### Other

* Update Dependencies ([#561](https://github.com/samply/blaze/pull/561))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/22?closed=1).

## v0.14.0

### New Features

* Allow Disabling Referential Integrity ([#544](https://github.com/samply/blaze/pull/544))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/11?closed=1).

## v0.13.5

### Bugfixes

* Fix Insufficient Configured Threads for the Metrics Server ([#557](https://github.com/samply/blaze/pull/557))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/21?closed=1).

## v0.13.4

### Bugfixes

* Fix Health Handler ([#553](https://github.com/samply/blaze/pull/553))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/19?closed=1).

## v0.13.3

### Bugfixes

* Fix Failing Metrics Endpoint ([#547](https://github.com/samply/blaze/pull/547))

### Security

* Fix CVE-2021-37137 in Package io.netty:netty-codec ([#548](https://github.com/samply/blaze/pull/548))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/18?closed=1).

## v0.13.2

### Security

* Migrate from Aleph to Jetty ([#538](https://github.com/samply/blaze/pull/538))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/17?closed=1).

## v0.13.1

### Bugfixes

* Fix Encoding of Parameters at Operation POST Requests ([#513](https://github.com/samply/blaze/issues/513))

### Other

* Move to OpenJDK ([#518](https://github.com/samply/blaze/issues/518))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/16?closed=1).

## v0.13.0

### New Features

* Implement CQL ParameterDef and ParameterRef ([#506](https://github.com/samply/blaze/issues/506))

### Bugfixes

* Implement Normalization of Conditional Operators ([#504](https://github.com/samply/blaze/issues/504))

### Other

* Update Java to v17 ([#503](https://github.com/samply/blaze/issues/503))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/15?closed=1).

## v0.12.2

### Bugfixes

* Fix DB Sync After Failing Transaction ([#498](https://github.com/samply/blaze/issues/498))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/14?closed=1).

## v0.12.1

### Bugfixes

* Fix Failing OPTIONS Request ([#490](https://github.com/samply/blaze/issues/490))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/13?closed=1).

## v0.12.0

### New Features

* Support _profile Search Parameter ([#427](https://github.com/samply/blaze/issues/427))
* Support _lastUpdated Search Parameter ([#428](https://github.com/samply/blaze/issues/428))
* Add Measure Evaluation Duration to MeasureReport ([#437](https://github.com/samply/blaze/issues/437))
* Implement Read-Only Transactions ([#440](https://github.com/samply/blaze/issues/440))
* Implement Subject Parameter in $evaluate-measure ([#451](https://github.com/samply/blaze/issues/451))
* Ensure Linearizability on Single Resource Read and Write Operations ([#450](https://github.com/samply/blaze/issues/450))

### Performance Improvements

* Tune RocksDB Settings for Improving Imports ([#432](https://github.com/samply/blaze/issues/432))

### Bugfixes

* Ensure Next-Links in Search Results can be Resolved by GET ([#463](https://github.com/samply/blaze/issues/463))
* Fix Resizing of Buffers at Read ([#475](https://github.com/samply/blaze/issues/475))
* Fix Indexing of lastUpdated for Deleted Resources ([#469](https://github.com/samply/blaze/issues/469))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/10?closed=1).

## v0.11.1

### Security

* Migrate to from AdoptOpenJDK Eclipse Temurin ([#462](https://github.com/samply/blaze/issues/462))

## v0.11.0

**!!! IMPORTANT !!!**

The database schema has changed! Please start with a fresh database docker volume/directory.

### New Features

* Implement Search Param _include ([#345](https://github.com/samply/blaze/issues/345))
* Implement Search Param _revinclude ([#342](https://github.com/samply/blaze/issues/342))
* Implement Conditional Create ([#359](https://github.com/samply/blaze/issues/359))
* Allow Multiple Includes with same Type ([#351](https://github.com/samply/blaze/issues/351))
* Fall Back to Literal Reference Resolution on $evaluate-measure ([#357](https://github.com/samply/blaze/issues/357))
* Use Implementation of ge/le for gt/lt in Date Search Params ([#410](https://github.com/samply/blaze/issues/410))
* Override the base URL when Forwarded Headers are Present ([#408](https://github.com/samply/blaze/issues/408))
* Implement Search Parameters of Type Number ([#391](https://github.com/samply/blaze/issues/391))

### Performance Improvements

* Improve Transaction Performance ([#373](https://github.com/samply/blaze/issues/373))
* Refactor Reference Extraction ([#368](https://github.com/samply/blaze/issues/368))
* Introduce Record for Attachment ([#364](https://github.com/samply/blaze/issues/364))
* Implement a Transaction Cache ([#340](https://github.com/samply/blaze/issues/340))
* Create Instance and Versioned URLs by Hand ([#339](https://github.com/samply/blaze/issues/339))
* Use LUID's instead of Random UUID's ([#338](https://github.com/samply/blaze/issues/338))
* Improve Performance of JSON Bundle Encoding ([#336](https://github.com/samply/blaze/issues/336))
* Bundle Entries of a Page Should be a Vector ([#318](https://github.com/samply/blaze/issues/318))
* Improve Performance of JSON Unforming ([#308](https://github.com/samply/blaze/issues/308))
* Improve Performance of Resource Handle Function ([#307](https://github.com/samply/blaze/issues/307))
* Improve Hashing Performance ([#297](https://github.com/samply/blaze/issues/297))
* Use Jsonista for Better JSON Encoding/Decoding Performance ([#34](https://github.com/samply/blaze/issues/34))

### Other Improvements

* Fix and Enhance OpenID Connect Auth ([#372](https://github.com/samply/blaze/issues/372))
* Rename CQL Context Unspecified into Unfiltered ([#317](https://github.com/samply/blaze/issues/317))
* Migrate to a Java 15 Runtime ([#315](https://github.com/samply/blaze/issues/315))

### Bugfixes

* Fix Total Counter on Recreating a Resource ([#341](https://github.com/samply/blaze/issues/341))
* Fix FHIR Date Search ([#327](https://github.com/samply/blaze/issues/327))
* Fix Inconsistent Paged Results on Disjunctive FHIR Searches ([#324](https://github.com/samply/blaze/issues/324))
* Fix JSON Generation of Instant Values ([#320](https://github.com/samply/blaze/issues/320))
* Make Lists of Values of OR Search Parameters Unique ([#293](https://github.com/samply/blaze/issues/293))
* Fix Issue Parsing of Large CQL Queries Never Finishes ([#214](https://github.com/samply/blaze/issues/214))
