(ns blaze.db.impl.index.rts-as-of
  "Common index entry generation for the three indices:

   * ResourceAsOf
   * TypeAsOf
   * SystemAsOf"
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.fhir.hash :as hash])
  (:import
   [clojure.lang Numbers]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- state ^long [^long num-changes op]
  (cond-> (bit-shift-left num-changes 8)
    (identical? :create op) (Numbers/setBit 1)
    (identical? :delete op) (Numbers/setBit 0)))

(defn- encode-value
  ([hash num-changes op]
   (-> (bb/allocate rao/min-value-size)
       (hash/into-byte-buffer! hash)
       (bb/put-long! (state num-changes op))
       bb/array))
  ([hash num-changes op purged-at]
   (-> (bb/allocate rao/max-value-size)
       (hash/into-byte-buffer! hash)
       (bb/put-long! (state num-changes op))
       (bb/put-long! purged-at)
       bb/array)))

(defn index-entries
  ([tid id t hash num-changes op]
   (let [value (encode-value hash num-changes op)]
     [[:resource-as-of-index (rao/encode-key tid id t) value]
      [:type-as-of-index (tao/encode-key tid t id) value]
      [:system-as-of-index (sao/encode-key t tid id) value]]))
  ([tid id t hash num-changes op purged-at]
   (let [value (encode-value hash num-changes op purged-at)]
     [[:resource-as-of-index (rao/encode-key tid id t) value]
      [:type-as-of-index (tao/encode-key tid t id) value]
      [:system-as-of-index (sao/encode-key t tid id) value]])))
