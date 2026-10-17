(ns blaze.db.impl.cache-spec
  (:require
   [blaze.db.impl.cache :as cache]
   [clojure.spec.alpha :as s]))

(s/fdef cache/max-size-in-bytes
  :args (s/cat :name string? :max-size-ratio (s/and number? (complement neg?)))
  :ret nat-int?)
