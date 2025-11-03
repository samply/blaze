(ns blaze.db.impl.index.index-handle
  (:refer-clojure :exclude [conj])
  (:require
   [blaze.db.impl.codec :as codec])
  (:import
   [blaze.db.impl.index IndexHandle SingleVersionId]))

(set! *warn-on-reflection* true)

(defn from-single-version-id
  "Creates an index handle from `single-version-id`."
  [single-version-id]
  (IndexHandle/fromSingleVersionId ^SingleVersionId single-version-id))

(defn from-resource-handle
  "Creates an index handle from `resource-handle`."
  [resource-handle]
  (IndexHandle/fromIdAndHash (codec/id-byte-string (:id resource-handle))
                             (:hash resource-handle)))

(defn id
  "Returns the id of `index-handle`."
  [index-handle]
  (.id ^IndexHandle index-handle))

(defn hash-prefixes
  "Returns a list of hash prefixes as integers of `index-handle`."
  [index-handle]
  (.hashPrefixes ^IndexHandle index-handle))

(defn to-single-version-ids [index-handle]
  (.toSingleVersionIds ^IndexHandle index-handle))

(defn matches-hash?
  "Tests whether `index-handle` contains the prefix of `hash`."
  [index-handle hash]
  (.matchesHash ^IndexHandle index-handle hash))

(defn conj
  "Returns a new index-handle with the hash of `single-version-id` added to the
  hashes of `index-handle`."
  [index-handle single-version-id]
  (.conj ^IndexHandle index-handle single-version-id))

(defn id-comp
  "Compares `index-handle-1` and `index-handle-2` by id."
  [index-handle-1 index-handle-2]
  (.compareTo (.id ^IndexHandle index-handle-1) (.id ^IndexHandle index-handle-2)))

(defn intersection
  "Returns a new index-handle with the intersection of the hashes found in both
  `index-handle-1` and `index-handle-2`.

  Throws an exception if the IDs of both index handles aren't equal."
  [index-handle-1 index-handle-2]
  (.intersection ^IndexHandle index-handle-1 index-handle-2))

(defn union
  "Returns a new index-handle with the union of the hashes found in both
  `index-handle-1` and `index-handle-2`.

  Throws an exception if the IDs of both index handles aren't equal."
  [index-handle-1 index-handle-2]
  (.union ^IndexHandle index-handle-1 index-handle-2))
