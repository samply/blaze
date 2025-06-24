(ns blaze.elm.ratio-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.elm.quantity :as quantity]
   [blaze.elm.quantity-spec]
   [blaze.elm.ratio :as ratio]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef ratio/ratio
  :args (s/cat :numerator quantity/quantity? :denominator quantity/quantity?))
