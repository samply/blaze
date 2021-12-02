(ns blaze.elm.equiv-relationships-spec
  (:require
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]))


(s/fdef equiv-relationships/find-equiv-rels-library
  :args (s/cat :library :elm/library))
