(ns blaze.db.impl.index.rts-as-of-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-val [byte-array]
  (let [buf (bb/wrap byte-array)
        hash (hash/from-byte-buffer! buf)
        state (bb/get-long! buf)]
    {:hash hash
     :num-changes (rh/state->num-changes state)
     :op (rh/state->op state)}))
