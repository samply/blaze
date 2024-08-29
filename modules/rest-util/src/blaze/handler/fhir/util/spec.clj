(ns blaze.handler.fhir.util.spec
  (:require
   [blaze.rest-api :as-alias rest-api]
   [clojure.spec.alpha :as s]))

(s/def :ring.request.query-params/key
  string?)

(s/def :ring.request.query-params/value
  (s/or :string string? :strings (s/coll-of string? :min-count 1)))

(s/def :ring.request/query-params
  (s/map-of :ring.request.query-params/key :ring.request.query-params/value))

(s/def ::rest-api/batch-handler
  fn?)

;; in milliseconds
(s/def ::rest-api/db-sync-timeout
  pos-int?)
