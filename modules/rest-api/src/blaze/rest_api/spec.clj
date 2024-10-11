(ns blaze.rest-api.spec
  (:require
   [blaze.db.spec]
   [blaze.executors :as ex]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.operation :as-alias operation]
   [blaze.spec]
   [buddy.auth.protocols :as p]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]))

(set! *warn-on-reflection* true)

(s/def :blaze/rest-api
  fn?)

(s/def ::rest-api/admin-node
  :blaze.db/node)

(s/def ::rest-api/auth-backends
  (s/coll-of #(satisfies? p/IAuthentication %)))

(s/def ::rest-api/search-system-handler
  fn?)

(s/def ::rest-api/transaction-handler
  fn?)

(s/def ::rest-api/history-system-handler
  fn?)

(s/def ::rest-api/async-status-handler
  fn?)

(s/def ::rest-api/async-status-cancel-handler
  fn?)

(s/def ::rest-api/capabilities-handler
  fn?)

(s/def :blaze.rest-api.resource-pattern/type
  (s/or :name string? :default #{:default}))

(def interaction-code?
  #{:read
    :vread
    :update
    :patch
    :delete
    :delete-history
    :conditional-delete-type
    :history-instance
    :history-type
    :create
    :search-type})

(s/def :blaze.rest-api.interaction/handler
  (s/or :ref ig/ref? :handler fn?))

(s/def :blaze.rest-api.interaction/doc
  string?)

(s/def ::rest-api/interaction
  (s/keys
   :req
   [:blaze.rest-api.interaction/handler]
   :opt
   [:blaze.rest-api.interaction/doc]))

;; Interactions keyed there code
(s/def :blaze.rest-api.resource-pattern/interactions
  (s/map-of interaction-code? ::rest-api/interaction))

(s/def ::rest-api/resource-pattern
  (s/keys
   :req
   [:blaze.rest-api.resource-pattern/type
    :blaze.rest-api.resource-pattern/interactions]))

(s/def ::rest-api/resource-patterns
  (s/coll-of ::rest-api/resource-pattern))

(s/def :blaze.rest-api.compartment/search-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def ::rest-api/compartment
  (s/keys
   :req
   [:blaze.rest-api.compartment/code
    :blaze.rest-api.compartment/search-handler]))

(s/def ::rest-api/compartments
  (s/coll-of ::rest-api/compartment))

(s/def ::operation/code
  string?)

(s/def ::operation/def-uri
  string?)

(s/def ::operation/affects-state
  boolean?)

(s/def ::operation/response-type
  #{:json :binary})

(s/def ::operation/resource-types
  (s/coll-of string?))

(s/def ::operation/system-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def ::operation/type-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def ::operation/instance-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def ::operation/documentation
  string?)

(s/def ::rest-api/operation
  (s/keys
   :req
   [::operation/code
    ::operation/def-uri]
   :opt
   [::operation/affects-state
    ::operation/response-type
    ::operation/resource-types
    ::operation/system-handler
    ::operation/type-handler
    ::operation/instance-handler
    ::operation/documentation]))

(s/def ::rest-api/operations
  (s/coll-of ::rest-api/operation))

(s/def :blaze.rest-api.json-parse/executor
  ex/executor?)

(s/def ::rest-api/structure-definitions
  (s/coll-of map?))
