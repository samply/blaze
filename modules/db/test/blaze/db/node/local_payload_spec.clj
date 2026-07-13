(ns blaze.db.node.local-payload-spec
  (:require
   [blaze.db.node.local-payload :as lp]
   [blaze.db.node.local-payload.spec]
   [clojure.spec.alpha :as s]))

(s/fdef lp/wrap
  :args (s/cat :entries :blaze.db.node.local-payload/entries)
  :ret :blaze.db.node.local-payload/payload)

(s/fdef lp/unwrap
  :args (s/cat :payload :blaze.db.node.local-payload/payload)
  :ret (s/nilable :blaze.db.node.local-payload/entries))
