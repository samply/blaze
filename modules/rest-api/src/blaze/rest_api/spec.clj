(ns blaze.rest-api.spec
  (:require
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig])
  (:import
    [buddy.auth.protocols IAuthentication]))


(s/def :blaze.rest-api/auth-backends
  (s/coll-of #(satisfies? IAuthentication %)))


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


(s/def :blaze.rest-api/version
  string?)


(s/def :blaze.rest-api/context-path
  string?)


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
     :blaze.rest-api.operation/def-uri
     :blaze.rest-api.operation/resource-types]
    :opt
    [:blaze.rest-api.operation/system-handler
     :blaze.rest-api.operation/type-handler
     :blaze.rest-api.operation/instance-handler]))


(s/def :blaze.rest-api/operations
  (s/coll-of :blaze.rest-api/operation))
