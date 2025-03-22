# Resource Store Protocol and KV Implementation

This module contains the protocol for Resource Store backend implementations. In addition to the protocol, an implementation using the Key Value Store protocols is provided.

## Resource Store

The Resource Store holds all versions of all resources by their content hash. This approach is similar to Git which keeps commits by their SHA1 content hashes. Using the content hash instead of the resource id ensures key-value pairs are never updated and so are easily cacheable. On top of that, older versions of resources are still available for the [FHIR History][1] interactions. 

## Resource Store Protocol

A Resource Store has to implement the `ResourceStore` protocol that can be found in the `blaze.db.resource-store` namespace.

## KV Implementation

An implementation using the Key Value Store protocols can be found in the `blaze.db.resource-store.kv` namespace. That namespace will provide a component with the key `:blaze.db.resource-store/kv` and can be configured in the following way:

```clojure
:blaze.db.resource-store/kv
   {:kv-store #blaze/ref :blaze.db/resource-kv-store
    :parsing-context #blaze/ref :blaze.fhir/parsing-context
    :executor #blaze/ref :blaze.db.resource-store.kv/executor}

:blaze.db.resource-store.kv/executor {}

[:blaze.db.kv/mem :blaze.db/resource-kv-store] {:column-families {}}
```

In the above configuration example, the implementation for the Key Value Store protocols is the In-Memory Key Value Store but other implementations like RocksDB are possible.

[1]: <https://www.hl7.org/fhir/http.html#history>
