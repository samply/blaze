(ns blaze.db.resource-store.spec
  (:require
    [blaze.db.resource-store :as rs]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(defn resource-store? [x]
  (satisfies? rs/ResourceStore x))


(s/def :blaze.db/resource-store
  resource-store?)
