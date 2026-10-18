(ns blaze.db.impl.index.type-as-of-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.util :refer [read-t!]]))

(set! *unchecked-math* :warn-on-boxed)

(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:type (codec/tid->type (bb/get-int! buf))
     :t (read-t! buf)
     :id (bb/get-iso-8859-1-string! buf (bb/remaining buf))}))
