(ns blaze.elm.quantity-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.elm.quantity :as quantity]
   [clojure.spec.alpha :as s])
  (:import
   [javax.measure Unit]))

(defn unit? [x]
  (instance? Unit x))

(s/fdef quantity/format-unit
  :args (s/cat :unit unit?))

(s/fdef quantity/quantity
  :args (s/cat :value number? :unit string?))
