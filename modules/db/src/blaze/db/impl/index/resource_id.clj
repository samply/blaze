(ns blaze.db.impl.index.resource-id
  "Functions for accessing the ResourceId index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv])
  (:import
    [com.google.common.primitives Longs]
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn encode-key [tid ^String id]
  (-> (bb/allocate (+ (int codec/tid-size) (.length id)))
      (bb/put-int! tid)
      (bb/put-byte-array! (.getBytes id StandardCharsets/ISO_8859_1))
      bb/array))


(defn resource-id [kv-store]
  (fn [tid id]
    (some-> (kv/get kv-store :resource-id-index (encode-key tid id))
            Longs/fromByteArray)))


(defn encode-value [did]
  (Longs/toByteArray did))


(defn index-entry [tid id did]
  [:resource-id-index (encode-key tid id) (encode-value did)])
