(ns blaze.elm.ratio-spec
  (:refer-clojure :exclude [ratio?])
  (:require
    [blaze.anomaly-spec]
    [blaze.elm.quantity :as quantity]
    [blaze.elm.ratio :as ratio]
    [clojure.spec.alpha :as s])
  (:import
    [blaze.elm.ratio Ratio]))


(defn ratio? [x]
  (instance? Ratio x))


(s/fdef ratio/ratio
   :args (s/cat :numerator quantity/quantity? :denominator quantity/quantity?))
