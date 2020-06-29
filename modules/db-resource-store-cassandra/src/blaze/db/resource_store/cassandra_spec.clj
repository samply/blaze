(ns blaze.db.resource-store.cassandra-spec
  (:require
    [blaze.async-comp-spec]
    [blaze.db.resource-store.cassandra :refer [new-cassandra-resource-store]]
    [blaze.db.resource-store.cassandra.spec]
    [clojure.spec.alpha :as s])
  (:import
    [com.datastax.oss.driver.api.core ConsistencyLevel]))


(s/fdef new-cassandra-resource-store
  :args (s/cat :contact-points :blaze.db.resource-store.cassandra/contact-points
               :key-space :blaze.db.resource-store.cassandra/key-space
               :put-consistency-level #(instance? ConsistencyLevel %)))
