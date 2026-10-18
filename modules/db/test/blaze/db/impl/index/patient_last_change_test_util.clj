(ns blaze.db.impl.index.patient-last-change-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.util :refer [read-t!]]))

(set! *unchecked-math* :warn-on-boxed)

(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)
        patient-id-len (- (bb/remaining buf) codec/t-size)]
    {:patient-id (bb/get-iso-8859-1-string! buf patient-id-len)
     :t (read-t! buf)}))
