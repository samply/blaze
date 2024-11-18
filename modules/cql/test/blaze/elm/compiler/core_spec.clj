(ns blaze.elm.compiler.core-spec
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef core/expr?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef core/static?
  :args (s/cat :x any?)
  :ret boolean?)
