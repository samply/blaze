(ns blaze.interaction.spec
  (:require
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]))


(s/def :fhir.router.match/data
  (s/keys :req [:blaze/base-url]))


(s/def :fhir.router/match
  (s/keys :req-un [:fhir.router.match/data]))


(s/def :blaze.rest-api.resource-pattern/type
  string?)


(def interaction-code?
  #{:read
    :vread
    :update
    :patch
    :delete
    :history-instance
    :history-type
    :create
    :search-type})


(s/def :blaze.rest-api.interaction/handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api/interaction
  (s/keys
    :req
    [:blaze.rest-api.interaction/handler]))


;; Interactions keyed there code
(s/def :blaze.rest-api.resource-pattern/interactions
  (s/map-of interaction-code? :blaze.rest-api/interaction))


(s/def :blaze.rest-api/resource-pattern
  (s/keys
    :req
    [:blaze.rest-api.resource-pattern/type
     :blaze.rest-api.resource-pattern/interactions]))


(s/def :blaze.rest-api/resource-patterns
  (s/coll-of :blaze.rest-api/resource-pattern))


(s/def :blaze.rest-api/transaction-handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api/history-system-handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api.operation/code
  string?)


(s/def :blaze.rest-api.operation/resource-types
  (s/coll-of string?))


(s/def :blaze.rest-api.operation/system-handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api.operation/type-handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api.operation/instance-handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api/operation
  (s/keys
    :req
    [:blaze.rest-api.operation/code
     :blaze.rest-api.operation/resource-types]
    :opt
    [:blaze.rest-api.operation/system-handler
     :blaze.rest-api.operation/type-handler
     :blaze.rest-api.operation/instance-handler]))


(s/def :blaze.rest-api/operations
  (s/coll-of :blaze.rest-api/operation))


(s/def :blaze/rest-api
  (s/keys
    :req
    [:blaze.rest-api/resource-patterns]
    :opt
    [:blaze.rest-api/transaction-handler
     :blaze.rest-api/history-system-handler
     :blaze.rest-api/operations]))
