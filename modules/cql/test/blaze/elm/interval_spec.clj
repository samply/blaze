(ns blaze.elm.interval-spec
  (:require
    [blaze.elm.date-time :refer [temporal?]]
    [blaze.elm.interval :as interval]
    [blaze.elm.quantity :refer [quantity?]]
    [clojure.spec.alpha :as s]))


(defn point?
  "Returns true if `x` is of a valid point type for an interval."
  [x]
  (or (int? x) (decimal? x) (temporal? x) (quantity? x)))


(s/fdef interval/interval
  :args (s/cat :start (s/nilable point?) :end (s/nilable point?)))
