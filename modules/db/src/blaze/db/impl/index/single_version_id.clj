(ns blaze.db.impl.index.single-version-id
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh])
  (:import
   [blaze.db.impl.index SingleVersionId]
   [blaze.fhir Hash]))

(set! *warn-on-reflection* true)

(defn single-version-id [id hash]
  (SingleVersionId. id (.prefix ^Hash hash)))

(defn from-resource-handle [resource-handle]
  (SingleVersionId. (codec/id-byte-string (rh/id resource-handle))
                    (.prefix ^Hash (rh/hash resource-handle))))

(defn id [single-version-id]
  (.id ^SingleVersionId single-version-id))

(defn hash-prefix [single-version-id]
  (bit-and (.hashPrefix ^SingleVersionId single-version-id) 0xFFFFFFFF))
