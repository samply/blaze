(ns blaze.db.resource-store.spec
  (:require
   [blaze.db.resource-store :as rs]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db/resource-store
  #(satisfies? rs/ResourceStore %))

(s/def ::rs/key
  (s/tuple :fhir/type :blaze.resource/hash :blaze.resource/variant))
