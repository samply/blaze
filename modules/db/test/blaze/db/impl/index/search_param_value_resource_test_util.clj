(ns blaze.db.impl.index.search-param-value-resource-test-util
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
  (let [id-size (bb/get-byte! buf (- (bb/limit buf) hash/prefix-size 1))
        value-size (- (bb/remaining buf) id-size 2 hash/prefix-size
                      codec/c-hash-size codec/tid-size)
        c-hash (bb/get-int! buf)
        tid (bb/get-int! buf)
        value (bs/from-byte-buffer! buf value-size)
        _ (bb/get-byte! buf)
        id (bs/from-byte-buffer! buf id-size)
        _ (bb/get-byte! buf)]
    {:code (codec/c-hash->code c-hash (Integer/toHexString c-hash))
     :type (codec/tid->type tid)
     :v-hash value
     :id (codec/id-string id)
     :hash-prefix (hash/prefix-from-byte-buffer! buf)}))

(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (->> (i/keys snapshot :search-param-value-index decode-key-human bs/empty)
         (into [] (map #(mapv % keys))))))
