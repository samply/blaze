(ns blaze.db.impl.index.type-as-of-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct 128))
  ([buf]
   {:type (codec/tid->type (bb/get-int! buf))
    :t (codec/descending-long (bb/get-long! buf))
    :id (codec/id-string (bs/from-byte-buffer! buf (bb/remaining buf)))}))


(defn decode-value-human
  ([] (bb/allocate-direct (+ hash/size Long/BYTES)))
  ([buf]
   (let [hash (bs/from-byte-buffer! buf hash/size)
         state (bb/get-long! buf)]
     {:hash hash
      :num-changes (rh/state->num-changes state)
      :op (rh/state->op state)})))


(defn decode-index-entry [[k v]]
  [(decode-key-human (bb/wrap k))
   (decode-value-human (bb/wrap v))])
