(ns blaze.elm.type-infer-spec
  (:require
    [blaze.elm.spec]
    [blaze.elm.type-infer :as type-infer]
    [clojure.spec.alpha :as s]))


(s/def :life/source-type
  string?)


(s/fdef type-infer/infer-types
  :args (s/cat :context any? :expression :elm/expression))


(s/fdef type-infer/infer-library-types
  :args (s/cat :library :elm/library))
