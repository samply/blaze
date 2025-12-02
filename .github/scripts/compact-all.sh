#!/bin/bash -e

base="http://localhost:8080/fhir"

blazectl --server "$base" compact index search-param-value-index
blazectl --server "$base" compact index resource-value-index
blazectl --server "$base" compact index compartment-search-param-value-index
blazectl --server "$base" compact index compartment-resource-type-index
blazectl --server "$base" compact index active-search-params
blazectl --server "$base" compact index tx-success-index
blazectl --server "$base" compact index tx-error-index
blazectl --server "$base" compact index t-by-instant-index
blazectl --server "$base" compact index resource-as-of-index
blazectl --server "$base" compact index type-as-of-index
blazectl --server "$base" compact index system-as-of-index
blazectl --server "$base" compact index patient-last-change-index
blazectl --server "$base" compact index type-stats-index
blazectl --server "$base" compact index system-stats-index
blazectl --server "$base" compact index cql-bloom-filter
blazectl --server "$base" compact index cql-bloom-filter-by-t
blazectl --server "$base" compact resource default
