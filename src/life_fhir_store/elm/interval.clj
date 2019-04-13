(ns life-fhir-store.elm.interval
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.protocols :as p]))


(defrecord Interval [low high low-closed high-closed])


(s/fdef interval
  :args (s/cat :low any? :high any? :low-closed boolean? :high-closed boolean?))

(defn interval [low high low-closed high-closed]
  (->Interval low high low-closed high-closed))
