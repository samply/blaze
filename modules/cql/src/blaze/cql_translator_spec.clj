(ns blaze.cql-translator-spec
  (:require
    [blaze.cql-translator :as cql-translator]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]))


(s/fdef cql-translator/translate
  :args (s/cat :cql string? :opts (s/* some?))
  :ret :elm/library)
