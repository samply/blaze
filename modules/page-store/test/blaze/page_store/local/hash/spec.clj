(ns blaze.page-store.local.hash.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [com.google.common.hash HashCode]))

(set! *warn-on-reflection* true)

(s/def :blaze.page-store.local/hash-code
  (s/and #(instance? HashCode %) #(= 256 (.bits ^HashCode %))))
