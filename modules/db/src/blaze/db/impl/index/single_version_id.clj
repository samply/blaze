(ns blaze.db.impl.index.single-version-id
  (:import
   [blaze.db.impl.index SingleVersionId]
   [blaze.fhir Hash]))

(set! *warn-on-reflection* true)

(defn single-version-id [id hash]
  (SingleVersionId. id (.prefix ^Hash hash)))

(defn id [single-version-id]
  (.id ^SingleVersionId single-version-id))

(defn hash-prefix [single-version-id]
  (bit-and (.hashPrefix ^SingleVersionId single-version-id) 0xFFFFFFFF))
