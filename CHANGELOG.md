# Changelog

## v0.12.0-rc.2

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
