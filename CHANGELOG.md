# Changelog

## v0.11.0

**!!! IMPORTANT !!!**

The database schema has changed! Please start with a fresh database docker volume/directory.

### New Features

* Implement Search Param _include (#345)
* Implement Search Param _revinclude (#342)
* Implement Conditional Create (#359)
* Allow Multiple Includes with same Type (#351)
* Fall Back to Literal Reference Resolution on $evaluate-measure (#357)
* Use Implementation of ge/le for gt/lt in Date Search Params (#410)
* Override the base URL when Forwarded Headers are Present (#408)
* Implement Search Parameters of Type Number (#391)

### Performance Improvements

* Improve Transaction Performance (#373)
* Refactor Reference Extraction (#368)
* Introduce Record for Attachment (#364)
* Implement a Transaction Cache (#340)
* Create Instance and Versioned URLs by Hand (#339)
* Use LUID's instead of Random UUID's (#338)
* Improve Performance of JSON Bundle Encoding (#336)
* Bundle Entries of a Page Should be a Vector (#318)
* Improve Performance of JSON Unforming (#308)
* Improve Performance of Resource Handle Function (#307)
* Improve Hashing Performance (#297)
* Use Jsonista for Better JSON Encoding/Decoding Performance (#34)

### Other Improvements

* Fix and Enhance OpenID Connect Auth (#372)
* Rename CQL Context Unspecified into Unfiltered (#317)
* Migrate to a Java 15 Runtime (#315)

### Bugfixes

* Fix Total Counter on Recreating a Resource (#341)
* Fix FHIR Date Search (#327)
* Fix Inconsistent Paged Results on Disjunctive FHIR Searches (#324)
* Fix JSON Generation of Instant Values (#320)
* Make Lists of Values of OR Search Parameters Unique (#293)
* Fix Issue Parsing of Large CQL Queries Never Finishes (#214)
