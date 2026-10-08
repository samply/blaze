(ns blaze.page-store.cache.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(s/def :blaze.page-store/cache
  #(instance? Cache %))
