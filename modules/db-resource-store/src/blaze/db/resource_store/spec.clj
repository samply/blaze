(ns blaze.db.resource-store.spec
  (:require
    [blaze.db.resource-store :refer [ResourceLookup ResourceStore]]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/resource-lookup
  #(satisfies? ResourceLookup %))


(s/def :blaze.db/resource-store
  (s/and #(satisfies? ResourceLookup %)
         #(satisfies? ResourceStore %)))
