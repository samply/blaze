(ns blaze.page-store.caching.spec
  (:require
   [blaze.page-store.caching :as-alias caching]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::caching/backing-store
  :blaze.page-store/backing-store)

(s/def ::caching/expire-duration
  time/duration?)
