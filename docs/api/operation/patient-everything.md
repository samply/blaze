# Operation Patient Everything <Badge type="warning" text="v0.22.0"/>

The official documentation can be found [here][1].

* filtering returned resources by a date range using the `start` and `end` parameters is supported
  * the search parameter [clinical-date][2] will be used for filtering. Resources of all resource types covered by that search parameter will be filtered.
* has a fix limit of 10,000 resources if paging isn't used
* paging is supported when the `_count` parameter is used
* no other params are supported

[1]: <https://www.hl7.org/fhir/operation-patient-everything.html>
[2]: <http://hl7.org/fhir/R4/searchparameter-registry.html#clinical-date>
