(ns blaze.db.impl.index.single-version-id
  (:import
   [blaze.db.impl.index ResourceHandle SingleVersionId]
   [blaze.fhir Hash]))

(set! *warn-on-reflection* true)

(defn single-version-id [id hash]
  (SingleVersionId. id (.prefix ^Hash hash)))

(defn from-resource-handle [resource-handle]
  (SingleVersionId/fromResourceHandle ^ResourceHandle resource-handle))

(defn id [single-version-id]
  (.id ^SingleVersionId single-version-id))

(defn hash-prefix [single-version-id]
  (bit-and (.hashPrefix ^SingleVersionId single-version-id) 0xFFFFFFFF))

(defn matches-hash?
  "Tests whether `single-version-id` has the hash prefix of `resource-handle`."
  [single-version-id resource-handle]
  (.matchesHashPrefix ^SingleVersionId single-version-id
                      ^ResourceHandle resource-handle))
