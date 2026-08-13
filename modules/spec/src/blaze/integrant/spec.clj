(ns blaze.integrant.spec
  (:require
   [blaze.integrant :as-alias bi]
   [clojure.spec.alpha :as s]))

(s/def ::bi/composite-key
  (s/tuple qualified-keyword? qualified-keyword?))

(s/def ::bi/key
  (s/or :simple qualified-keyword?
        :composite ::bi/composite-key))
