#!/bin/bash -e

BASE="http://localhost:8080/fhir"

blazectl --server "$BASE" compact index search-param-value-index
blazectl --server "$BASE" compact index resource-value-index
blazectl --server "$BASE" compact index compartment-search-param-value-index
blazectl --server "$BASE" compact index compartment-resource-type-index
blazectl --server "$BASE" compact index active-search-params
blazectl --server "$BASE" compact index tx-success-index
blazectl --server "$BASE" compact index tx-error-index
blazectl --server "$BASE" compact index t-by-instant-index
blazectl --server "$BASE" compact index resource-as-of-index
blazectl --server "$BASE" compact index type-as-of-index
blazectl --server "$BASE" compact index system-as-of-index
blazectl --server "$BASE" compact index patient-last-change-index
blazectl --server "$BASE" compact index type-stats-index
blazectl --server "$BASE" compact index system-stats-index
blazectl --server "$BASE" compact index cql-bloom-filter
blazectl --server "$BASE" compact index cql-bloom-filter-by-t
blazectl --server "$BASE" compact resource default
