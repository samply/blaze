(ns blaze.terminology-service-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.terminology-service :as terminology-service :refer [terminology-service?]]
   [clojure.spec.alpha :as s]))

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
  :ret ac/completable-future?)
