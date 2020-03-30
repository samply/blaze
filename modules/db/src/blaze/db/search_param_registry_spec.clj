(ns blaze.db.search-param-registry-spec
  (:require
    [blaze.fhir.spec]
    [blaze.db.search-param-registry :as sr]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/search-param-registry
  #(satisfies? sr/SearchParamRegistry %))


;; TODO: figure out how to include search-param-spec here
(s/fdef sr/get
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :code string? :type string?)
  :ret (s/nilable some?))


;; TODO: figure out how to include search-param-spec here
(s/fdef sr/linked-compartments
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :resource :blaze/resource)
  :ret (s/coll-of some?))
