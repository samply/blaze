(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
    [blaze.db.api-spec]
    [blaze.elm.compiler-spec]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom])
  (:import
    [java.time OffsetDateTime]))


(s/fdef cql/evaluate-expression
  :args (s/cat :db :blaze.db/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :expression-name string?)
  :ret (s/or :count nat-int? :anomaly ::anom/anomaly))


(s/fdef cql/calc-stratums
  :args (s/cat :db :blaze.db/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :population-expression-name string?
               :stratum-expression-name string?)
  :ret (s/or :stratums (s/map-of some? nat-int?) :anomaly ::anom/anomaly))


(s/fdef cql/calc-mult-component-stratums
  :args (s/cat :db :blaze.db/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :population-expression-name string?
               :expression-names (s/coll-of string?)))
