(ns blaze.elm.expression.cache.codec-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.db.kv.spec]
   [blaze.elm.expression.cache :as-alias ec]
   [blaze.elm.expression.cache.bloom-filter.spec]
   [blaze.elm.expression.cache.codec :as codec]
   [clojure.spec.alpha :as s]))

(s/fdef codec/put-entry
  :args (s/cat :bloom-filter ::ec/bloom-filter)
  :ret :blaze.db.kv/write-entry)

(s/fdef codec/delete-entry
  :args (s/cat :bloom-filter ::ec/bloom-filter)
  :ret :blaze.db.kv/write-entry)
