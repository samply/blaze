(ns blaze.db.node.spec
  (:require
   [blaze.db.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def :blaze.db.node/poll-timeout
  time/duration?)

(s/def :blaze.db.node/queue-capacity
  pos-int?)

(s/def :blaze.db.node/nodes
  (s/map-of some? :blaze.db/node))
