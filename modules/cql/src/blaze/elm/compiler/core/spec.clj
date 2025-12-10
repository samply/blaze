(ns blaze.elm.compiler.core.spec
  (:require
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.core :as core]
   [clojure.spec.alpha :as s]))

(s/def ::core/resolve-function-def-context
  (s/keys :opt-un [::c/function-defs]))
