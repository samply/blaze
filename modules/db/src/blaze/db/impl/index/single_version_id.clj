(ns blaze.db.impl.index.single-version-id
  (:require
   [blaze.db.impl.codec :as codec])
  (:import
   [blaze.db.impl.index SingleVersionId]
   [blaze.fhir Hash]))

(set! *warn-on-reflection* true)

(defn single-version-id [id hash]
  (SingleVersionId. id (.prefix ^Hash hash)))

(defn from-resource-handle [resource-handle]
  (SingleVersionId. (codec/id-byte-string (:id resource-handle))
                    (.prefix ^Hash (:hash resource-handle))))

(defn id [single-version-id]
  (.id ^SingleVersionId single-version-id))

(defn hash-prefix [single-version-id]
  (bit-and (.hashPrefix ^SingleVersionId single-version-id) 0xFFFFFFFF))

(defn matches-hash?
  "Tests whether `single-version-id` has the prefix of `hash`."
  [single-version-id hash]
  (.matchesHash ^SingleVersionId single-version-id hash))
