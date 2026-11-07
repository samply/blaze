(ns blaze.db.permissions.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.permissions/write-access-control
  (s/coll-of string? :kind set?))

(s/def :blaze.db/permissions
  (s/keys :opt-un [:blaze.db.permissions/write-access-control]))
