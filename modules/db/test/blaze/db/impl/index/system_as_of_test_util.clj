(ns blaze.db.impl.index.system-as-of-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:t (codec/descending-long (bb/get-5-byte-long! buf))
     :type (codec/tid->type (bb/get-int! buf))
     :did (bb/get-long! buf)}))


(defn decode-val [byte-array]
  (let [buf (bb/wrap byte-array)
        hash (hash/from-byte-buffer! buf)
        state (bb/get-long! buf)]
    {:hash hash
     :num-changes (rh/state->num-changes state)
     :op (rh/state->op state)
     :id (codec/id-from-byte-buffer buf)}))
