(ns blaze.db.impl.index.compartment.search-param-value-resource-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.test-util :as tu]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.fhir.hash :as hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- decode-key-human
  ([] (bb/allocate-direct 128))
  ([buf]
   {:compartment
    [(let [c-hash (bb/get-int! buf)]
       (tu/co-c-hash->code c-hash (Integer/toHexString c-hash)))
     (bb/get-long! buf)]
    :code (let [c-hash (bb/get-int! buf)]
            (codec/c-hash->code c-hash (Integer/toHexString c-hash)))
    :type (codec/tid->type (bb/get-int! buf))
    :v-hash (let [size (- (bb/remaining buf) hash/prefix-size codec/did-size 1)]
              (bs/from-byte-buffer! buf size))
    :did (do (bb/get-byte! buf)
            (bb/get-long! buf))
    :hash-prefix (hash/prefix-from-byte-buffer! buf)}))


(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :compartment-search-param-value-index)]
    (into [] (map #(mapv % keys)) (i/keys! iter decode-key-human bs/empty))))
