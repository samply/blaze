(ns blaze.elm.compiler-spec
  (:require
    [blaze.elm.compiler :as compiler]
    [blaze.elm.compiler.protocols :refer [Expression]]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom])
  (:import
    [java.time OffsetDateTime]))


(set! *warn-on-reflection* true)


(s/def :life/expression
  #(satisfies? Expression %))


(s/def ::now
  #(satisfies? OffsetDateTime %))


(s/def ::library-context
  (s/map-of string? some?))


(s/def ::eval-context
  (s/keys :req-un [:blaze.db/db ::now] :opt-un [::library-context]))


(s/def ::compile-context
  (s/keys :req-un [:elm/library]))


(s/fdef compiler/compile
  :args (s/cat :context ::compile-context :expression :elm/expression)
  :ret :life/expression)


(s/def :life/compiled-expression-defs
  (s/map-of :elm/name :life/expression))


(s/def :life/compiled-library
  (s/keys :req [:life/compiled-expression-defs]))


(s/fdef compiler/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library :life/compiled-library :anomaly ::anom/anomaly))


(s/fdef compiler/compile-with-equiv-clause
  :args (s/cat :context ::compile-context :with-equiv-clause :elm.query.life/with-equiv)
  :ret fn?)
