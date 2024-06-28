(ns blaze.elm.expression.cache.codec.by-t-spec
  (:require
   [blaze.db.kv.spec]
   [blaze.elm.expression.cache :as-alias ec]
   [blaze.elm.expression.cache.bloom-filter.spec]
   [blaze.elm.expression.cache.codec.by-t :as codec-by-t]
   [clojure.spec.alpha :as s]))

(s/fdef codec-by-t/put-entry
  :args (s/cat :bloom-filter ::ec/bloom-filter)
  :ret :blaze.db.kv/write-entry)

(s/fdef codec-by-t/delete-entry
  :args (s/cat :bloom-filter ::ec/bloom-filter)
  :ret :blaze.db.kv/write-entry)
