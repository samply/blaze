(ns blaze.elm.quantity-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.elm.quantity :as q]
    [clojure.spec.alpha :as s])
  (:import
    [javax.measure Unit]))


(defn unit? [x]
  (instance? Unit x))


(s/fdef q/format-unit
  :args (s/cat :unit unit?))


(s/fdef q/quantity
  :args (s/cat :value number? :unit string?))
