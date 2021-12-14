(ns blaze.rest-api.spec
  (:require
    [blaze.executors :as ex]
    [blaze.spec]
    [blaze.structure-definition]
    [buddy.auth.protocols :as p]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]))


(set! *warn-on-reflection* true)


(s/def :blaze/rest-api
  fn?)


(s/def :blaze.rest-api/auth-backends
  (s/coll-of #(satisfies? p/IAuthentication %)))


(s/def :blaze.rest-api/search-system-handler
  fn?)


(s/def :blaze.rest-api/transaction-handler
  fn?)


(s/def :blaze.rest-api/history-system-handler
  fn?)


(s/def :blaze.rest-api.resource-pattern/type
  (s/or :name string? :default #{:default}))


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


(s/def :blaze.rest-api.interaction/doc
  string?)


(s/def :blaze.rest-api/interaction
  (s/keys
    :req
    [:blaze.rest-api.interaction/handler]
    :opt
    [:blaze.rest-api.interaction/doc]))


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


(s/def :blaze.rest-api.compartment/search-handler
  (s/or :ref ig/ref? :handler fn?))


(s/def :blaze.rest-api/compartment
  (s/keys
    :req
    [:blaze.rest-api.compartment/code
     :blaze.rest-api.compartment/search-handler]))


(s/def :blaze.rest-api/compartments
  (s/coll-of :blaze.rest-api/compartment))


(s/def :blaze.rest-api.operation/code
  string?)


(s/def :blaze.rest-api.operation/def-uri
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
     :blaze.rest-api.operation/def-uri]
    :opt
    [:blaze.rest-api.operation/resource-types
     :blaze.rest-api.operation/system-handler
     :blaze.rest-api.operation/type-handler
     :blaze.rest-api.operation/instance-handler]))


(s/def :blaze.rest-api/operations
  (s/coll-of :blaze.rest-api/operation))


(s/def :blaze.rest-api.json-parse/executor
  ex/executor?)


(s/def :blaze.rest-api/structure-definitions
  (s/coll-of :fhir.un/StructureDefinition))


;; in milliseconds
(s/def :blaze.rest-api/db-sync-timeout
  pos-int?)
