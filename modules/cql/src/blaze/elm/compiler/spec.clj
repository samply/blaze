(ns blaze.elm.compiler.spec
  (:require
   [blaze.db.spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.elm.compiler/expression
  core/expr?)

(s/def :blaze.elm.compiler/function
  fn?)

(s/def :elm/compile-context
  (s/keys :req-un [:elm/library :blaze.db/node]))
