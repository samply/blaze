(ns blaze.elm.expression.cache.spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.elm.expression.cache :as-alias ec]
   [blaze.elm.expression.cache.bloom-filter.spec]
   [blaze.executors :as ex]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::ec/max-size-in-mb
  nat-int?)

(s/def ::ec/refresh
  time/duration?)

(s/def ::ec/executor
  ex/executor?)

(s/def ::ec/num-threads
  pos-int?)
