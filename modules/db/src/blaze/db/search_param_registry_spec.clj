(ns blaze.db.search-param-registry-spec
  (:require
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef sr/get
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :code string? :type :fhir.resource/type)
  :ret (s/nilable :blaze.db/search-param))

(s/fdef sr/list-by-type
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :type :fhir.resource/type)
  :ret (s/coll-of :blaze.db/search-param :kind vector?))

(s/fdef sr/list-by-target
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :target string?)
  :ret (s/coll-of :blaze.db/search-param :kind vector?))

(s/fdef sr/linked-compartments
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :resource :blaze/resource)
  :ret (s/or :compartments (s/coll-of (s/tuple string? :blaze.resource/id))
             :anomaly ::anom/anomaly))

(s/fdef sr/compartment-resources
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :compartment-type :fhir.resource/type
               :type (s/? :fhir.resource/type))
  :ret (s/coll-of (s/tuple :fhir.resource/type string?)))
