(ns blaze.elm.compiler.query-spec
  (:require
    [blaze.elm.compiler.query :as query]
    [clojure.spec.alpha :as s]))


(s/fdef query/with-xform-factory
  :args (s/cat :with-clause fn?))

