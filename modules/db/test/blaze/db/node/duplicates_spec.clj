(ns blaze.db.node.duplicates-spec
  (:require
   [blaze.db.node.duplicates :as dup]
   [blaze.db.node.duplicates.spec]
   [clojure.spec.alpha :as s]))

(s/fdef dup/find-duplicate
  :args (s/cat :cmds (s/coll-of map? :kind vector?))
  :ret (s/nilable ::dup/duplicate))

(s/fdef dup/duplicate-msg
  :args (s/cat :earlier-cmd map? :later-cmd map?)
  :ret string?)
