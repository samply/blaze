(ns blaze.db.impl.index.system-stats-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.index.util :refer [read-t!]]))

(set! *unchecked-math* :warn-on-boxed)

(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:t (read-t! buf)}))

(defn decode-val [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:total (bb/get-long! buf)
     :num-changes (bb/get-long! buf)}))
