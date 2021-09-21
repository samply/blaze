(ns blaze.elm.compiler.arithmetic-operators-spec
  (:require
    [blaze.elm.compiler.arithmetic-operators :as ao]
    [clojure.spec.alpha :as s]))


(s/fdef ao/max-value
  :args (s/cat :type (s/nilable string?)))


(s/fdef ao/min-value
  :args (s/cat :type (s/nilable string?)))
