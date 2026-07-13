(ns blaze.db.node.local-payload.spec
  (:require
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s])
  (:import
   [java.lang.ref SoftReference]))

(s/def :blaze.db.node.local-payload/payload
  #(instance? SoftReference %))

(s/def :blaze.db.node.local-payload/entries
  (s/map-of :blaze.resource/hash :fhir/Resource))
