(ns blaze.elm.expression.cache.codec.form-spec
  (:require
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.bloom-filter.spec]
   [blaze.elm.expression.cache.codec.form :as codec-form]
   [clojure.spec.alpha :as s]))

(s/fdef codec-form/hash
  :args (s/cat :expr-form ::bloom-filter/expr-form)
  :ret ::bloom-filter/hash)
