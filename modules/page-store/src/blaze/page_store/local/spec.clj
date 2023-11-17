(ns blaze.page-store.local.spec
  (:require
   [blaze.page-store.local :as-alias local]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::local/max-size-in-mb
  nat-int?)

(s/def ::local/expire-duration
  time/duration?)
