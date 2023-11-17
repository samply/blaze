(ns blaze.handler.fhir.util.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :ring.request.query-params/key
  string?)

(s/def :ring.request.query-params/value
  (s/or :string string? :strings (s/coll-of string? :min-count 2)))

(s/def :ring.request/query-params
  (s/map-of :ring.request.query-params/key :ring.request.query-params/value))
