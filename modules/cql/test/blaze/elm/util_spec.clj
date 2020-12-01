(ns blaze.elm.util-spec
  (:require
    [blaze.elm.spec]
    [blaze.elm.util :as elm-util]
    [clojure.spec.alpha :as s]))


(s/fdef elm-util/parse-qualified-name
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/tuple string? string?)))
