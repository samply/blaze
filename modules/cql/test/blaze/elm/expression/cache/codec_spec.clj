(ns blaze.elm.expression.cache.codec-spec
  (:require
    [blaze.db.kv.spec]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.expression.cache :as-alias ec]
    [blaze.elm.expression.cache.codec :as codec]
    [blaze.elm.expression.cache.spec]
    [clojure.spec.alpha :as s]))


(s/fdef codec/index-entry
  :args (s/cat :bloom-filter ::ec/bloom-filter :expression core/expr?)
  :ret :blaze.db.kv/put-entry)
