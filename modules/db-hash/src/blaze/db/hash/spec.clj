(ns blaze.db.hash.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [com.google.common.hash HashCode]))


(s/def :blaze.db.resource/hash
  (s/and #(instance? HashCode %) #(= 256 (.bits ^HashCode %))))
