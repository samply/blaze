(ns blaze.terminology-service
  (:require
    [clojure.spec.alpha :as s]
    [manifold.deferred :refer [deferred?]]))


(defprotocol TermService
  (-expand-value-set [_ params]))


(defn term-service? [x]
  (satisfies? TermService x))


(s/def ::url
  string?)


(s/def ::valueSetVersion
  string?)


(s/def ::filter
  string?)


(def expand-value-set-params
  (s/keys :opt-un [::url ::valueSetVersion ::filter]))


(s/fdef expand-value-set
  :args (s/cat :term-service term-service? :params expand-value-set-params)
  :ret deferred?)

(defn expand-value-set [term-service params]
  (-expand-value-set term-service params))
