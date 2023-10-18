(ns blaze.db.impl.index.resource-search-param-value-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.fhir.hash :as hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human [buf]
  (let [tid (bb/get-int! buf)
        id-size (bb/size-up-to-null buf)
        id (bs/from-byte-buffer! buf id-size)
        _ (bb/get-byte! buf)
        hash-prefix (hash/prefix-from-byte-buffer! buf)
        c-hash (bb/get-int! buf)]
    {:type (codec/tid->type tid)
     :id (codec/id-string id)
     :hash-prefix hash-prefix
     :code (codec/c-hash->code c-hash (Integer/toHexString c-hash))
     :v-hash (bs/from-byte-buffer! buf)}))


(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :resource-value-index)]
    (into [] (map #(mapv % keys)) (i/keys! iter decode-key-human (bs/from-hex "00")))))
