(ns blaze.db.impl.index.resource-search-param-value-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.fhir.hash :as hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct r-sp-v/key-buffer-capacity))
  ([buf]
   (let [tid (bb/get-int! buf)
         did (bb/get-long! buf)
         hash-prefix (hash/prefix-from-byte-buffer! buf)
         c-hash (bb/get-int! buf)]
     {:type (codec/tid->type tid)
      :did did
      :hash-prefix hash-prefix
      :code (codec/c-hash->code c-hash (Integer/toHexString c-hash))
      :v-hash (bs/from-byte-buffer! buf)})))


(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :resource-value-index)]
    (into [] (map #(mapv % keys)) (i/keys! iter decode-key-human (bs/from-hex "00")))))
