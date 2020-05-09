(ns blaze.db.search-param-registry-spec
  (:require
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/search-param-registry
  #(satisfies? sr/SearchParamRegistry %))


(s/def :blaze.db/search-param
  #(satisfies? search-param/SearchParam %))


(s/fdef sr/get
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :code string? :type string?)
  :ret (s/nilable :blaze.db/search-param))


(s/fdef sr/list-by-type
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :type string?)
  :ret (s/coll-of :blaze.db/search-param))


(s/fdef sr/linked-compartments
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :resource :blaze/resource)
  :ret (s/coll-of some?))
