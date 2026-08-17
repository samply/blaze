(ns blaze.db.node.subscription-spec
  (:require
   [blaze.async.flow :as flow]
   [blaze.db.node.subscription :as sub]
   [blaze.db.node.subscription.spec]
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/fdef sub/subscription
  :args (s/cat :config :blaze.db.node.subscription/config
               :subscriber flow/subscriber?)
  :ret sub/subscription?)

(s/fdef sub/queued-t
  :args (s/cat :subscription sub/subscription?)
  :ret :blaze.db/t)

(s/fdef sub/queued
  :args (s/cat :subscription sub/subscription?)
  :ret nat-int?)

(s/fdef sub/unexamined
  :args (s/cat :subscription sub/subscription? :t :blaze.db/t)
  :ret nat-int?)

(s/fdef sub/window-t
  :args (s/cat :subscription sub/subscription? :t :blaze.db/t)
  :ret (s/nilable :blaze.db/t))

(s/fdef sub/publish!
  :args (s/cat :subscription sub/subscription? :t :blaze.db/t
               :changed-handles (s/coll-of (s/coll-of :blaze.db/resource-handle) :kind vector?))
  :ret nil?)

(s/fdef sub/ended?
  :args (s/cat :subscription sub/subscription?)
  :ret boolean?)

(s/fdef sub/wait-until-finished!
  :args (s/cat :subscription sub/subscription?))

(s/fdef sub/close!
  :args (s/cat :subscription sub/subscription? :t :blaze.db/t))

(s/fdef sub/close-exceptionally!
  :args (s/cat :subscription sub/subscription? :e #(instance? Throwable %)))
