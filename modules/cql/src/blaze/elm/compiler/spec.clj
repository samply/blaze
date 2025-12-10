(ns blaze.elm.compiler.spec
  (:require
   [blaze.db.spec]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def ::c/expression
  core/expr?)

(s/def ::c/function
  fn?)

(s/def ::c/eval-context
  (s/or :unfiltered #{"Unfiltered"} :resource-type :fhir.resource/type))

(s/def :elm/compile-context
  (s/keys :req-un [:elm/library ::c/eval-context :blaze.db/node]
          :opt-un [::c/function-defs :blaze/terminology-service]))
