(ns blaze.db.impl.index.search-param-value-resource-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.fhir.hash :as hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct sp-vr/key-buffer-capacity))
  ([buf]
   (let [value-size (- (bb/remaining buf) 1 codec/did-size hash/prefix-size
                       sp-vr/base-key-size)
         c-hash (bb/get-int! buf)
         tid (bb/get-int! buf)
         value (bs/from-byte-buffer! buf value-size)
         _ (bb/get-byte! buf)
         did (bb/get-long! buf)]
     {:code (codec/c-hash->code c-hash (Integer/toHexString c-hash))
      :type (codec/tid->type tid)
      :v-hash value
      :did did
      :hash-prefix (hash/prefix-from-byte-buffer! buf)})))


(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :search-param-value-index)]
    (into [] (map #(mapv % keys)) (i/keys! iter decode-key-human bs/empty))))
