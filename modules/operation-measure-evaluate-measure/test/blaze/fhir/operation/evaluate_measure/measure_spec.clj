(ns blaze.fhir.operation.evaluate-measure.measure-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.cql.translator-spec]
   [blaze.db.spec]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.spec]
   [blaze.fhir.operation.evaluate-measure :as-alias evaluate-measure]
   [blaze.fhir.operation.evaluate-measure.cql-spec]
   [blaze.fhir.operation.evaluate-measure.measure :as measure]
   [blaze.fhir.operation.evaluate-measure.measure.spec]
   [blaze.fhir.spec]
   [blaze.http.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [reitit.core :as reitit])
  (:import
   [java.time.temporal Temporal]))

(s/def ::context
  (s/keys :req [:blaze/base-url ::reitit/router]
          :opt [:blaze/cancelled? ::expr/cache]
          :req-un [:blaze/clock :blaze/rng-fn :blaze.db/db
                   ::evaluate-measure/executor]
          :opt-un [::evaluate-measure/timeout]))

(defn- temporal? [x]
  (instance? Temporal x))

(s/def ::period
  (s/tuple temporal? temporal?))

(s/def ::params
  (s/keys
   :req-un
   [::period
    ::measure/report-type]
   :opt-un
   [::measure/subject-ref]))

(s/fdef measure/evaluate-measure
  :args (s/cat :context ::context :measure :blaze/resource :params ::params)
  :ret ac/completable-future?)
