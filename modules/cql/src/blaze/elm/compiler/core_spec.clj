(ns blaze.elm.compiler.core-spec
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core.spec]
   [blaze.elm.compiler.library.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef core/expr?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef core/static?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef core/function-call-form
  :args (s/cat :name string? :op-names (s/coll-of string?) :body-form any?
               :op-forms (s/coll-of any?))
  :ret list?)

(s/fdef core/display-form
  :args (s/cat :expression core/expr?)
  :ret any?)

(s/fdef core/resolve-function-def
  :args (s/cat :context ::core/resolve-function-def-context :name string?
               :arity nat-int?)
  :ret (s/or :expr core/expr? :anomaly ::anom/anomaly))

(s/fdef core/compile-function
  :args (s/cat :context ::core/resolve-function-def-context :name string?
               :operands (s/coll-of core/expr?))
  :ret (s/or :expr core/expr? :anomaly ::anom/anomaly))
