(ns blaze.db.impl.index.rts-as-of-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.hash :as hash])
  (:import
   [blaze.db.impl.index ResourceHandle]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn decode-val [byte-array]
  (let [buf (bb/wrap byte-array)
        hash (hash/from-byte-buffer! buf)
        state (bb/get-long! buf)]
    (cond->
     {:hash hash
      :num-changes (ResourceHandle/numChanges state)
      :op (ResourceHandle/op state)}
      (<= 8 (bb/remaining buf))
      (assoc :purged-at (bb/get-long! buf)))))
