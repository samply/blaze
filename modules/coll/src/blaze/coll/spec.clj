(ns blaze.coll.spec
  (:import
   [clojure.lang IReduceInit]))

(defn coll-of [_pred]
  #(instance? IReduceInit %))
