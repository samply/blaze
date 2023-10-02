(ns blaze.db.impl.index.patient-last-change-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]))

(set! *unchecked-math* :warn-on-boxed)

(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)
        patient-id-len (- (bb/remaining buf) codec/t-size)]
    {:patient-id (codec/id-string (bs/from-byte-buffer! buf patient-id-len))
     :t (codec/descending-long (bb/get-long! buf))}))
