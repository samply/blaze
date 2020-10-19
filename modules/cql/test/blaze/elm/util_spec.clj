(ns blaze.elm.util-spec
  (:require
    [blaze.elm.spec]
    [blaze.elm.util :as elm-util]
    [clojure.spec.alpha :as s]))


(s/fdef elm-util/parse-qualified-name
  :args (s/cat :s (s/nilable string?))
  :ret (s/nilable (s/tuple string? string?)))


(s/fdef elm-util/named-type-specifier?
  :args (s/cat :type-specifier (s/nilable :elm/type-specifier))
  :ret boolean?)


(s/fdef elm-util/tuple-type-specifier?
  :args (s/cat :type-specifier (s/nilable :elm/type-specifier))
  :ret boolean?)


(s/fdef elm-util/choice-type-specifier?
  :args (s/cat :type-specifier (s/nilable :elm/type-specifier))
  :ret boolean?)


(s/fdef elm-util/list-type-specifier?
  :args (s/cat :type-specifier (s/nilable :elm/type-specifier))
  :ret boolean?)
