<script setup>
import { data } from "./performance.data";
</script>

# Performance

## CQL

The CQL engine of Blaze can process hundreds of thousands patients per second and scales with CPU cores available. 

<BarChart :data="data['simple-code-search-100k.txt']"
  title="Simple Code Search - Dataset 100k"
  x-label="System" :x-col="3"
  y-label="Patients/s" :y-col="8" :y-max="2200"
  :series="['2 % hits', '60 % hits', '100 % hits']" />

More information can be found in the [CQL Performance](performance/cql.md) section.

## FHIR Search

A section about FHIR Search performance can be found [here](performance/fhir-search.md).

## Load Testing

A section about load testing can be found [here](performance/load-testing.md).

## GraphQL

A section about GraphQL performance can be found [here](performance/graphql.md).
