(ns blaze.page-store.local.spec
  (:require
   [blaze.page-store.local :as-alias local]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::local/expire-duration
  time/duration?)

(s/def ::local/backing-store
  :blaze/page-store)
