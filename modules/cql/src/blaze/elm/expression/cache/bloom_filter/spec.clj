(ns blaze.elm.expression.cache.bloom-filter.spec
  (:require
   [blaze.elm.expression.cache :as-alias ec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.elm.expression.cache.codec BloomFilterContainer]
   [com.google.common.hash BloomFilter]))

(s/def ::bloom-filter/t
  :blaze.db/t)

(s/def ::bloom-filter/expr-form
  string?)

(s/def ::bloom-filter/patient-count
  nat-int?)

(s/def ::bloom-filter/filter
  #(instance? BloomFilter %))

(s/def ::bloom-filter/mem-size
  nat-int?)

(s/def ::ec/bloom-filter
  #(instance? BloomFilterContainer %))
