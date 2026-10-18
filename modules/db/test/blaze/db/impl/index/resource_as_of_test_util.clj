(ns blaze.db.impl.index.resource-as-of-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.util :refer [read-t!]]))

(set! *unchecked-math* :warn-on-boxed)

(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)
        tid (bb/get-int! buf)
        id-size (- (bb/remaining buf) codec/t-size)]
    {:type (codec/tid->type tid)
     :id (bb/get-iso-8859-1-string! buf id-size)
     :t (read-t! buf)}))
