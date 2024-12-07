(ns blaze.terminology-service.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.expand-value-set :as-alias expand-vs]
   [blaze.terminology-service.expand-value-set.request :as-alias request]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]))

(defn terminology-service? [x]
  (satisfies? p/TerminologyService x))

(s/def :blaze/terminology-service
  terminology-service?)

(s/def ::request/url
  string?)

(s/def ::request/value-set-version
  string?)

(s/def ::request/count
  nat-int?)

(s/def ::request/include-definition
  boolean?)

;; parameters are taken from: http://hl7.org/fhir/R4/valueset-operation-expand.html
(s/def ::expand-vs/request
  (s/keys
   :opt-un
   [:blaze.resource/id
    ::request/url
    ::request/value-set-version
    ::request/count
    ::request/include-definition]))
