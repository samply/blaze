(ns blaze.elm.normalizer-spec
  (:require
   [blaze.elm.normalizer :as normalizer]
   [clojure.spec.alpha :as s]))

(s/fdef normalizer/normalize-library
  :args (s/cat :library :elm/library))
