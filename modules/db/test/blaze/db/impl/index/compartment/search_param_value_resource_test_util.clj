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

(defn decode-key-human [buf]
  (let [id-size (bb/get-byte! buf (- (bb/limit buf) hash/prefix-size 1))]
    {:compartment
     [(let [c-hash (bb/get-int! buf)]
        (tu/co-c-hash->code c-hash (Integer/toHexString c-hash)))
      (-> (bs/from-byte-buffer! buf (bb/size-up-to-null buf)) codec/id-string)]
     :code (let [_ (bb/get-byte! buf)
                 c-hash (bb/get-int! buf)]
             (codec/c-hash->code c-hash (Integer/toHexString c-hash)))
     :type (codec/tid->type (bb/get-int! buf))
     :v-hash (let [size (- (bb/remaining buf) hash/prefix-size id-size 2)]
               (bs/from-byte-buffer! buf size))
     :id (do (bb/get-byte! buf)
             (codec/id-string (bs/from-byte-buffer! buf id-size)))
     :hash-prefix (do (bb/get-byte! buf)
                      (hash/prefix-from-byte-buffer! buf))}))

(defn decode-index-entries [kv-store & keys]
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (->> (i/keys snapshot :compartment-search-param-value-index decode-key-human bs/empty)
         (into [] (map #(mapv % keys))))))
