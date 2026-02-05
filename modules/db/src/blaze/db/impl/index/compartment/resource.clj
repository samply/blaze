(ns blaze.db.impl.index.compartment.resource
  "Functions for accessing the CompartmentResourceType index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]))

(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long except-co-res-id-prefix-size
  (+ codec/c-hash-size 1 codec/tid-size))

(defn- key-prefix-size
  {:inline
   (fn [co-res-id]
     `(unchecked-add-int ~except-co-res-id-prefix-size (bs/size ~co-res-id)))}
  [co-res-id]
  (unchecked-add-int except-co-res-id-prefix-size (bs/size co-res-id)))

(defn- encode-key-buf
  "Encodes the full key."
  [compartment tid id]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (unchecked-add-int (key-prefix-size co-res-id) (bs/size id)))
        (bb/put-int! co-c-hash)
        (bb/put-null-terminated-byte-string! co-res-id)
        (bb/put-int! tid)
        (bb/put-byte-string! id))))

(defn index-entry
  "Returns an entry of the CompartmentResourceType index build from `compartment`,
  `tid` and `id`."
  [compartment tid id]
  [:compartment-resource-type-index
   (bb/array (encode-key-buf compartment tid id))
   bytes/empty])
