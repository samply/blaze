(ns blaze.db.permissions-spec
  (:require
   [blaze.db.permissions :as p]
   [blaze.db.permissions.spec]
   [clojure.spec.alpha :as s]))

(s/fdef p/may-write?
  :args (s/cat :permissions (s/nilable :blaze.db/permissions) :code string?)
  :ret boolean?)

(s/fdef p/decode
  :args (s/cat :x any?)
  :ret any?)
