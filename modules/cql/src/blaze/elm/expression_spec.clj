(ns blaze.elm.expression-spec
  (:require
   [blaze.db.api-spec]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.compiler.spec]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef expr/eval
  :args (s/cat :context ::expr/context
               :expression ::c/expression
               :resource (s/nilable ed/resource?)))
