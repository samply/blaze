(ns blaze.db.impl.index.compartment.resource-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.test-util :as tu]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct 128))
  ([buf]
   {:compartment
    [(let [c-hash (bb/get-int! buf)]
       (tu/co-c-hash->code c-hash (Integer/toHexString c-hash)))
     (let [id-size (bb/size-up-to-null buf)]
       (codec/id-string (bs/from-byte-buffer buf id-size)))]
    :type (do (bb/get-byte! buf) (codec/tid->type (bb/get-int! buf)))
    :id (codec/id-string (bs/from-byte-buffer buf))}))


(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :compartment-resource-type-index)]
    (into [] (map #(mapv % keys)) (i/keys! iter decode-key-human bs/empty))))
