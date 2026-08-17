(ns blaze.db.node.waiters.spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.node/waiters
  (s/map-of :blaze.db/t ac/completable-future? :kind sorted?))
