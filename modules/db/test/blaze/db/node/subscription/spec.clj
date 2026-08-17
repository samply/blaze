(ns blaze.db.node.subscription.spec
  (:require
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.node.subscription/type
  :fhir.resource/type)

(s/def :blaze.db.node.subscription/name
  string?)

(s/def :blaze.db.node.subscription/thread-name
  string?)

(s/def :blaze.db.node.subscription/queue-capacity
  pos-int?)

(s/def :blaze.db.node.subscription/queued-t
  :blaze.db/t)

(s/def :blaze.db.node.subscription/remove!
  ifn?)

(s/def :blaze.db.node.subscription/wake-node-publish-loop!
  ifn?)

(s/def :blaze.db.node.subscription/config
  (s/keys
   :req-un
   [:blaze.db.node.subscription/type
    :blaze.db.node.subscription/name
    :blaze.db.node.subscription/thread-name
    :blaze.db.node.subscription/queue-capacity
    :blaze.db.node.subscription/queued-t
    :blaze.db.node.subscription/remove!
    :blaze.db.node.subscription/wake-node-publish-loop!]))
