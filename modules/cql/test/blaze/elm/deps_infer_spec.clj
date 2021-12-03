(ns blaze.elm.deps-infer-spec
  (:require
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]))


(s/fdef deps-infer/infer-library-deps
  :args (s/cat :library :elm/library))
