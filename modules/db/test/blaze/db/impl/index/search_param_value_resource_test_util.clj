(ns blaze.db.impl.index.search-param-value-resource-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct 128))
  ([buf]
   (let [id-size (bb/get-byte! buf (- (bb/limit buf) codec/hash-prefix-size 1))
         value-size (- (bb/remaining buf) id-size 2 codec/hash-prefix-size
                       codec/c-hash-size codec/tid-size)
         c-hash (bb/get-int! buf)
         tid (bb/get-int! buf)
         value (bs/from-byte-buffer buf value-size)
         _ (bb/get-byte! buf)
         id (bs/from-byte-buffer buf id-size)
         _ (bb/get-byte! buf)]
     {:code (codec/c-hash->code c-hash (Integer/toHexString c-hash))
      :type (codec/tid->type tid)
      :v-hash value
      :id (codec/id-string id)
      :hash-prefix (bs/from-byte-buffer buf codec/hash-prefix-size)})))


(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :search-param-value-index)]
    (into [] (map #(mapv % keys)) (i/keys! iter decode-key-human bs/empty))))
