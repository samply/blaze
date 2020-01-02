(ns blaze.terminology-service-spec
  (:require
    [blaze.terminology-service :as terminology-service :refer [terminology-service?]]
    [clojure.spec.alpha :as s]
    [manifold.deferred :refer [deferred?]]))


(s/def ::url
  string?)


(s/def ::valueSetVersion
  string?)


(s/def ::filter
  string?)


(def expand-value-set-params
  (s/keys :opt-un [::url ::valueSetVersion ::filter]))


(s/fdef terminology-service/expand-value-set
  :args (s/cat :terminology-service terminology-service? :params expand-value-set-params)
  :ret deferred?)
