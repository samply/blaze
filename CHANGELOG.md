# Changelog

## v0.24.1

### Notes

Please update from v0.24.0 if you use variable length logical id's.

### Bugfixes

* Fix Error While Reading Non-Existent Resource ([#1475](https://github.com/samply/blaze/issues/1475))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/84?closed=1).
 
## v0.24.0

### Notes

This release contains performance improvements for FHIR search and CQL. Counting the number of resources by using `_summary=count` in FHIR search with more than one search parameter uses all cores now and so is up to 10 times faster than before. CQL queries which test the existence of multiple condition codes are up to twice as fast.

### Enhancements

* Implement Below Modifier for Canonical References ([#1418](https://github.com/samply/blaze/issues/1418))
* Add Admin UI ([#1408](https://github.com/samply/blaze/pull/1408))

### Bugfixes

* Fix FHIR Search Combination of Sorting and Token Search ([#1431](https://github.com/samply/blaze/issues/1431))
* FHIR Search _id Queries Should Not Return Deleted Patients ([#1415](https://github.com/samply/blaze/issues/1397))

### Performance

* Improve FHIR Search Count Performance ([#1466](https://github.com/samply/blaze/pull/1466))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/76?closed=1).

## v0.23.4

### Bugfixes

* Fix Error while Evaluating Measure with Population Basis Boolean ([#1397](https://github.com/samply/blaze/issues/1397))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/83?closed=1).

## v0.23.3

### Bugfixes

* Fix Quantity Comparison with Incompatible Units Fails ([#1385](https://github.com/samply/blaze/issues/1385))

### Minor Enhancements

* Implement Paging for the $everything Operation ([#1348](https://github.com/samply/blaze/issues/1348))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/81?closed=1).

## v0.23.2

### Bugfixes

* Include Supporting Resources in Patient $everything ([#1306](https://github.com/samply/blaze/issues/1306))

### Minor Enhancements

* Support filtering for elements in CapabilityStatement ([#1250](https://github.com/samply/blaze/issues/1250))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/80?closed=1).

## v0.23.1

### Bugfixes

* Fix Operation Patient $everything returns Duplicate Entries ([#1287](https://github.com/samply/blaze/issues/1287))

### Minor Enhancements

* Improve Error Reporting on CQL Parsing Problems ([#1275](https://github.com/samply/blaze/issues/1275))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/79?closed=1).

## v0.23.0

### Enhancements

* Implement Full Support for Relationship Queries ([#493](https://github.com/samply/blaze/issues/493))
* Implement FHIR Search Sorting by ID ([#1254](https://github.com/samply/blaze/issues/1254))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/74?closed=1).

## v0.22.3

### Bugfixes

* Fix Empty Results in Certain Reverse Chaining FHIR Search Queries ([#1215](https://github.com/samply/blaze/issues/1215))

### Performance

* Remove Usage of Small Direct Byte Buffers ([#1176](https://github.com/samply/blaze/pull/1176))
* Replace Allocation Heavy Functions ([#1198](https://github.com/samply/blaze/pull/1198))

### Operation

* Add Put Metric to Resource Store ([#1187](https://github.com/samply/blaze/pull/1187))

### Documentation

* Update CQL Performance Documentation ([#1177](https://github.com/samply/blaze/pull/1177))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/78?closed=1).

## v0.22.2

### Enhancements

* Index all Value Types of Numerical Search Params ([#1165](https://github.com/samply/blaze/issues/1165))

### Bugfixes

* Fix Error Handling on Non-Existing Patient ([#1104](https://github.com/samply/blaze/issues/1104))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/77?closed=1).

## v0.22.1

### Bugfixes

* Resolve Relative Attachment.url Values Like References ([#804](https://github.com/samply/blaze/issues/804))

### Performance

* Skip Indexing Unnecessary Compartment Values ([#1045](https://github.com/samply/blaze/pull/1045))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/75?closed=1).

## v0.22.0

It's not recommended to downgrade from this version to an older version because [#1041](https://github.com/samply/blaze/pull/1041) introduces a new transaction command called `keep` that would be ignored by older versions. However the transaction log is only read at the moment transactions happen or if the index is rebuild. So it will be ok to downgrade in emergency without rebuilding the index.

### New Features

* Implement Operation Patient $everything ([#1037](https://github.com/samply/blaze/pull/1037))
* Add Basic Frontend ([#951](https://github.com/samply/blaze/pull/951))
 
### Enhancements

* Ensure History Changes only if Resource Changes ([#1041](https://github.com/samply/blaze/pull/1041))

### Performance

* Improve Database Sync Efficiency ([#1039](https://github.com/samply/blaze/pull/1039))

### Bugfixes

* Fix Evaluate Measure Generating Duplicate List IDs ([#1036](https://github.com/samply/blaze/pull/1036))
* Fix Comparison of Length Result not Possible in CQL ([#1035](https://github.com/samply/blaze/pull/1035))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/68?closed=1).

## v0.21.0

### New Features

* Add Support for Custom Search Parameters ([#1025](https://github.com/samply/blaze/pull/1025))
* Add Link Headers ([#1003](https://github.com/samply/blaze/pull/1003))

### Performance

* Make Count Queries Parallel ([#998](https://github.com/samply/blaze/pull/998))

### Operation

* Add RocksDB Block Cache Usage Metrics ([#1008](https://github.com/samply/blaze/pull/1008))
* Add RocksDB Table Reader Usage Metric ([#1011](https://github.com/samply/blaze/pull/1011))

### Bugfixes

* Fix High Memory Usage of RocksDB ([#1030](https://github.com/samply/blaze/issues/1030))
* Fix Low Capacity of Paging Tokens ([#1029](https://github.com/samply/blaze/issues/1029))
* Fix Non-Stable Paging ([#1000](https://github.com/samply/blaze/pull/1000))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/64?closed=1).

## v0.20.6

### Bugfixes

* Fix Gender Values with Extensions Not Found in CQL ([#993](https://github.com/samply/blaze/issues/993))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/72?closed=1).

## v0.20.5

### Bugfixes

* Fix Extended Birth Date in CQL ([#985](https://github.com/samply/blaze/pull/985))
* Fix Extended Instant ([#984](https://github.com/samply/blaze/pull/984))

### Minor Enhancements

* Add Profiles to Capability Statement ([#983](https://github.com/samply/blaze/issues/983))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/71?closed=1).

## v0.20.4

### Bugfixes

* Fix Date Equal Search and Add Missing Prefixes ([#975](https://github.com/samply/blaze/pull/975))
* Handle Case of Missing Resource Contents ([#974](https://github.com/samply/blaze/pull/974))
* Fix Null Resource in Transactions Result in a 500 ([#969](https://github.com/samply/blaze/pull/969))

### Minor Enhancements

* Support for Search prefixes sa and eb ([#666](https://github.com/samply/blaze/issues/666))

### Performance

* Improve Date Search Performance ([#977](https://github.com/samply/blaze/pull/977))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/70?closed=1).

## v0.20.3

### Documentation

* Generate GitHub Pages from Documentation ([#955](https://github.com/samply/blaze/pull/955))

### Performance

* Improve Performance Evaluating Measures without Stratifier ([#962](https://github.com/samply/blaze/pull/962))
* Strip Narrative from Structure Definitions ([#959](https://github.com/samply/blaze/pull/959))

### Minor Enhancements

* Add First Link to Searchset Bundles ([#961](https://github.com/samply/blaze/pull/961))

### Operation

* Add Estimated Size to Cache Metrics ([#963](https://github.com/samply/blaze/pull/963))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/69?closed=1).

## v0.20.2

### Other

* Update Dependencies ([#943](https://github.com/samply/blaze/pull/943))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/65?closed=1).

## v0.20.1

### Bugfixes

* Fix URL Generation ([#932](https://github.com/samply/blaze/pull/932))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/63?closed=1).

## v0.20.0

### New Features

* Implement Special Search Parameter _elements ([#923](https://github.com/samply/blaze/pull/923))

* Implement First Parts of Operation $graphql ([#924](https://github.com/samply/blaze/pull/924))  

### Bugfixes

* Fix Indexing Error during Soundex Calculation ([#928](https://github.com/samply/blaze/pull/928))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/59?closed=1).

## v0.19.4

### Bugfixes

* Allow Writing Large Binary Resources in XML Format ([#919](https://github.com/samply/blaze/pull/919))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/62?closed=1).

## v0.19.3

### Bugfixes

* Fix System Search Paging ([#910](https://github.com/samply/blaze/pull/910))

### Documentation

* Extend Documentation of Data Sync ([#911](https://github.com/samply/blaze/pull/911))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/61?closed=1).

## v0.19.2

### Bugfixes

* Fix _lastUpdated Search Returning a Resource more than Once ([#882](https://github.com/samply/blaze/issues/882))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/60?closed=1).

## v0.19.1

### Security

* Update Dependencies ([#898](https://github.com/samply/blaze/pull/898))
* Update Dependencies ([#899](https://github.com/samply/blaze/pull/899))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/58?closed=1).

## v0.19.0

### New Features

* Add Evaluate Measure Timeout ([#888](https://github.com/samply/blaze/pull/888))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/38?closed=1).

## v0.18.6

### Documentation

* Enhance Development Docs ([#878](https://github.com/samply/blaze/pull/878))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/57?closed=1).

## v0.18.5

### New Features

* Support HTTP Header If-None-Match in Update Interactions ([#782](https://github.com/samply/blaze/issues/782))
* Add a Backport of R5 Quantity Stratum Values ([#853](https://github.com/samply/blaze/pull/853))
* Return CodeableConcepts as is for Strata ([#851](https://github.com/samply/blaze/pull/851))
* Implement CQL ToRatio ([#840](https://github.com/samply/blaze/pull/840))
* Implement CQL Concept Data Type ([#839](https://github.com/samply/blaze/pull/839))

### Bugfixes

* Fix Date Search ([#864](https://github.com/samply/blaze/pull/864))

### Operation

* Decrease Size of Docker Image ([#858](https://github.com/samply/blaze/pull/858))

## v0.18.4

### Bugfixes

* Fix CQL Function Argument Hiding ([#835](https://github.com/samply/blaze/pull/835))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/55?closed=1).

## v0.18.3

### Operation

* Revert Purging curl for Future Docker Health Checks ([#831](https://github.com/samply/blaze/pull/831))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/54?closed=1).

## v0.18.2

### Bugfixes

* Fix Storage of Bundles with References ([#822](https://github.com/samply/blaze/pull/822))

### Security

* Update Dependencies ([#824](https://github.com/samply/blaze/pull/824))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/53?closed=1).

## v0.18.1

### Security

* Update Dependencies ([#817](https://github.com/samply/blaze/pull/817))
* Uninstall wget because of CVE-2021-31879 ([#801](https://github.com/samply/blaze/pull/801))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/52?closed=1).

## v0.18.0

### New Features

* Allow Population Basis Differ from Subject in Measures ([#768](https://github.com/samply/blaze/pull/768))
* Implement Sorting by _lastUpdated ([#98](https://github.com/samply/blaze/issues/98))
* Allow Metadata Requests in Batches ([#781](https://github.com/samply/blaze/pull/781))
* Allow to Set Separate RocksDB WAL Dirs ([#791](https://github.com/samply/blaze/pull/791))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/36?closed=1).

## v0.17.12

### Security

* Migrate to Eclipse Temurin because OpenJDK is Deprecated ([#773](https://github.com/samply/blaze/issues/773))

### Bugfixes

* Remove Bare Polymorph JSON Properties ([#772](https://github.com/samply/blaze/pull/772))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/51?closed=1).

## v0.17.11

### Bugfixes

* Fix Quantity Indexing without Value ([#764](https://github.com/samply/blaze/issues/764))
* Fix Deserialisation of Primitive Values in Extensions ([#767](https://github.com/samply/blaze/issues/767))

### Other Improvements

* Implement Functions in CQL ([#766](https://github.com/samply/blaze/pull/766))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/50?closed=1).

## v0.17.10

### Bugfixes

* Fix Reference Resolution on Extended Primitive References ([#758](https://github.com/samply/blaze/issues/758))

### Other Improvements

* Implement CQL ConvertsToTime ([#759](https://github.com/samply/blaze/pull/759))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/48?closed=1).

## v0.17.9

### Other Improvements

* Implement CQL ToTime and rearrange ToDate and ToDateTime ([#747](https://github.com/samply/blaze/pull/747))
* Improve CQL Error Message on Subtract ([#755](https://github.com/samply/blaze/pull/755))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/47?closed=1).

## v0.17.8

### Other

* Switch to Media Type text/cql-identifier for CQL Expressions ([#748](https://github.com/samply/blaze/pull/748))
* Update Dependencies ([#749](https://github.com/samply/blaze/pull/749))
* Update Dependencies ([#746](https://github.com/samply/blaze/pull/746))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/46?closed=1).

## v0.17.7

### Other

* Introduce Database Versioning ([#738](https://github.com/samply/blaze/pull/738))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/45?closed=1).

## v0.17.6

### Performance

* Improve Interning of Complex Types ([#725](https://github.com/samply/blaze/issues/725))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/44?closed=1).

## v0.17.5

### Bugfixes

* Allow Extensions on Date Data Type ([#371](https://github.com/samply/blaze/issues/371))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/43?closed=1).

## v0.17.4

### Bugfixes

* Return an Error on Incorrect Content-Type for Search Requests ([#524](https://github.com/samply/blaze/issues/524))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/42?closed=1).

## v0.17.3

### Bugfixes

* Fix Content Negotiation ([#710](https://github.com/samply/blaze/pull/710))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/41?closed=1).

## v0.17.2

### Bugfixes

* Fix Server Errors Because of Unencoded Error Outputs ([#706](https://github.com/samply/blaze/pull/706))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/40?closed=1).

## v0.17.1

### Performance Improvements

* Implement Parallel Multi-Get in KV Resource Store ([#699](https://github.com/samply/blaze/pull/699))

* Intern Some Extensions ([#696](https://github.com/samply/blaze/pull/696))

### Operation

* Add Metrics to KV Resource Store ([#698](https://github.com/samply/blaze/pull/698))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/39?closed=1).

## v0.17.0

### New Features

* Chained Search Params ([#691](https://github.com/samply/blaze/pull/691))

* Implement CQL ToBoolean ([#682](https://github.com/samply/blaze/pull/682))

* Add Reverse Include Values into CapabilityStatement ([#688](https://github.com/samply/blaze/pull/688))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/30?closed=1).

## v0.16.5

### Security

* Update Jackson Databind to v2.13.2.2 ([#668](https://github.com/samply/blaze/pull/668))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/35?closed=1).

## v0.16.4

### Security

* Update Jackson Databind to v2.13.2.1 ([#659](https://github.com/samply/blaze/pull/659))
* Remove Unused Oracle Linux Packages ([#653](https://github.com/samply/blaze/pull/653))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/34?closed=1).

## v0.16.3

### Bugfixes

* Trim Values in FHIR Search ([#644](https://github.com/samply/blaze/pull/644))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/33?closed=1).

## v0.16.2

### Bugfixes

* Fix Authentication in Batch Requests ([#641](https://github.com/samply/blaze/pull/641))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/32?closed=1).

## v0.16.1

### Performance Improvements

* Improve CQL Quantity Creation ([#621](https://github.com/samply/blaze/pull/621))
* Improve FHIRPath Performance ([#617](https://github.com/samply/blaze/pull/617))
* Intern Uri, Canonical, Code, Coding and CodeableConcept ([#628](https://github.com/samply/blaze/pull/628))
* Use Records For HumanName and Address, Intern Meta ([#633](https://github.com/samply/blaze/pull/633))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/31?closed=1).

## v0.16.0

### New Features

* Implement CQL FHIRHelpers ToInterval Function ([#612](https://github.com/samply/blaze/pull/612))

The full changelog can be found [here](https://github.com/samply/blaze/milestone/23?closed=1).

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
