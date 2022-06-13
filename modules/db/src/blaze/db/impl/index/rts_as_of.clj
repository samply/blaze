(ns blaze.db.impl.index.rts-as-of
  "Common index entry generation for the three indices:

   * ResourceAsOf
   * TypeAsOf
   * SystemAsOf"
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.index.system-as-of :as sao]
    [blaze.db.impl.index.type-as-of :as tao]
    [blaze.fhir.hash :as hash])
  (:import
    [clojure.lang Numbers]
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- state ^long [^long num-changes op]
  (cond-> (bit-shift-left num-changes 8)
    (identical? :create op) (Numbers/setBit 1)
    (identical? :delete op) (Numbers/setBit 0)))


(defn encode-value [hash num-changes op ^String id]
  (-> (bb/allocate (+ hash/size codec/state-size (.length id)))
      (hash/into-byte-buffer! hash)
      (bb/put-long! (state num-changes op))
      (bb/put-byte-array! (.getBytes id StandardCharsets/ISO_8859_1))
      bb/array))


(defn index-entries [tid did t hash num-changes op id]
  (let [value (encode-value hash num-changes op id)]
    [[:resource-as-of-index (rao/encode-key tid did t) value]
     [:type-as-of-index (tao/encode-key tid t did) value]
     [:system-as-of-index (sao/encode-key t tid did) value]]))
