(ns blaze.fhir.operation.evaluate-measure.cql.spec
  (:require
   [blaze.elm.compiler :as-alias compiler]
   [blaze.elm.expression :as-alias expr]
   [blaze.fhir.operation.evaluate-measure :as-alias evaluate-measure]
   [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
   [blaze.fhir.operation.evaluate-measure.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::cql/timeout-eclipsed?
  ifn?)

(s/def ::cql/timeout
  time/duration?)

(s/def ::cql/context
  (s/merge
   ::expr/context
   (s/keys :req-un [::cql/timeout-eclipsed? ::cql/timeout
                    ::compiler/expression-defs])))

(s/def ::cql/return-handles?
  boolean?)

(s/def ::cql/population-basis
  (s/nilable :fhir.resource/type))

(s/def ::cql/evaluate-expression-context
  (s/merge
   ::cql/context
   (s/keys :req-un [::evaluate-measure/executor]
           :opt-un [::cql/return-handles? ::cql/population-basis])))
