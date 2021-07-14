(ns blaze.elm.compiler-spec
  (:require
    [blaze.elm.compiler :as compiler]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.spec]
    [blaze.elm.spec]
    [blaze.fhir.spec-spec]
    [clojure.spec.alpha :as s]))


(s/fdef compiler/compile
  :args (s/cat :context :elm/compile-context :expression :elm/expression)
  :ret core/expr?)
