(ns blaze.db.impl.index.rts-as-of
  "Common index entry generation for the three indices:

   * ResourceAsOf
   * TypeAsOf
   * SystemAsOf"
  (:require
   [blaze.byte-string-builder :as bsb]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.fhir.hash :as hash])
  (:import
   [blaze.db.impl.index ResourceHandle]
   [blaze.fhir Hash]
   [clojure.lang Numbers]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- state ^long [^long num-changes op]
  (cond-> (bit-shift-left num-changes 8)
    (identical? :create op) (Numbers/setBit 1)
    (identical? :delete op) (Numbers/setBit 0)))

(defn- encode-value [hash num-changes op]
  (-> (bsb/allocate rao/min-value-size)
      (hash/into-byte-string-builder! hash)
      (bsb/put-long! (state num-changes op))
      bsb/to-bytes))

(defn- encode-purged-value [resource-handle purged-at]
  (-> (bsb/allocate rao/max-value-size)
      (rh/hash-into-byte-string-builder! resource-handle)
      (bsb/put-long! (state (:num-changes resource-handle) (:op resource-handle)))
      (bsb/put-long! purged-at)
      bsb/to-bytes))

(defn- index-entries* [tid id t value]
  [[:resource-as-of-index (rao/encode-key tid id t) value]
   [:type-as-of-index (tao/encode-key tid t id) value]
   [:system-as-of-index (sao/encode-key t tid id) value]])

(defn index-entries
  "Returns the index entries of the resource version with `tid`, `id` and `t`."
  [tid id t hash num-changes op]
  (index-entries* tid id t (encode-value hash num-changes op)))

(defn resource-handle
  "Returns the resource handle of the resource version with `tid`, `id`, `t`,
  `hash`, `num-changes` and `op`, the one a reader decodes from the entries of
  `index-entries`."
  [tid id t hash num-changes op]
  (ResourceHandle. tid ^String id t ^Hash hash (state num-changes op)))

(defn purge-index-entries
  "Returns the index entries of the resource version of `resource-handle` with
  `tid` and `id`, marked as purged at `purged-at`."
  [tid id resource-handle purged-at]
  (index-entries* tid id (:t resource-handle)
                  (encode-purged-value resource-handle purged-at)))
