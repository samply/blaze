CodeSystem: ColumnFamily
Id: ColumnFamily
Title: "Column Family"
Description: "The column families of the databases Blaze stores its data in."
* insert ShareableCodeSystem
* #default
* #search-param-value-index
* #resource-value-index
* #compartment-search-param-value-index 
* #compartment-resource-type-index 
* #active-search-params 
* #tx-success-index 
* #tx-error-index 
* #t-by-instant-index 
* #resource-as-of-index 
* #type-as-of-index 
* #system-as-of-index 
* #patient-last-change-index 
* #type-stats-index 
* #system-stats-index 
* #cql-bloom-filter 
* #cql-bloom-filter-by-t 

ValueSet: ColumnFamily
Id: ColumnFamily
Title: "Column Family Value Set"
Description: "All column families of the databases Blaze stores its data in."
* insert ShareableValueSet
* include codes from system ColumnFamily
